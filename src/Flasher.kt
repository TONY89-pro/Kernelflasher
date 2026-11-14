package com.kernelflasher

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.FileOutputStream

object Flasher {

    const val ACTION_USB_PERMISSION = "com.kernelflasher.USB_PERMISSION"

    fun hasRoot(): Boolean = try {
        val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
        p.waitFor() == 0
    } catch (_: Exception) { false }

    fun displayName(ctx: Context, uri: Uri): String {
        ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && c.moveToFirst()) return c.getString(idx)
        }
        return uri.lastPathSegment ?: "archivo_seleccionado"
    }

    // --- DETECCIÓN DE DISPOSITIVOS OTG ---
    fun getConnectedUsbDevices(ctx: Context): List<UsbDevice> {
        val usbManager = ctx.getSystemService(Context.USB_SERVICE) as UsbManager
        return usbManager.deviceList.values.toList()
    }

    fun requestUsbPermission(ctx: Context, device: UsbDevice) {
        val usbManager = ctx.getSystemService(Context.USB_SERVICE) as UsbManager
        if (!usbManager.hasPermission(device)) {
            val intent = PendingIntent.getBroadcast(
                ctx, 0, Intent(ACTION_USB_PERMISSION),
                PendingIntent.FLAG_IMMUTABLE
            )
            usbManager.requestPermission(device, intent)
        }
    }

    // --- PREPARAR BINARIOS ADB Y FASTBOOT ---
    private fun prepareBinaries(ctx: Context, log: (String) -> Unit): Pair<File, File> {
        val binDir = File(ctx.filesDir, "bin").apply { mkdirs() }
        val fastbootFile = File(binDir, "fastboot")
        val adbFile = File(binDir, "adb")

        listOf("fastboot" to fastbootFile, "adb" to adbFile).forEach { (assetName, destFile) ->
            if (!destFile.exists() || destFile.length() == 0L) {
                try {
                    ctx.assets.open(assetName).use { input ->
                        FileOutputStream(destFile).use { output -> input.copyTo(output) }
                    }
                    destFile.setExecutable(true, false)
                } catch (e: Exception) {
                    log("[AVISO] Binario $assetName no encontrado en assets, usando sistema...")
                }
            }
        }
        return Pair(fastbootFile, adbFile)
    }

    // --- EJECUTAR COMANDO OTG (FASTBOOT / ADB) ---
    fun runOtgCommand(
        ctx: Context,
        tool: String, // "fastboot" o "adb"
        args: List<String>,
        log: (String) -> Unit
    ): Boolean {
        val (fbBin, adbBin) = prepareBinaries(ctx, log)
        val executable = if (tool == "fastboot" && fbBin.exists()) fbBin.absolutePath
        else if (tool == "adb" && adbBin.exists()) adbBin.absolutePath
        else tool

        val cmd = mutableListOf(executable).apply { addAll(args) }
        log("> Ejecutando: $tool ${args.joinToString(" ")}")

        return try {
            val pb = ProcessBuilder(cmd)
            pb.environment()["HOME"] = ctx.filesDir.absolutePath
            pb.environment()["TMPDIR"] = ctx.cacheDir.absolutePath
            pb.redirectErrorStream(true)
            val process = pb.start()
            process.inputStream.bufferedReader().forEachLine { log("  $it") }
            val code = process.waitFor()
            if (code == 0) log("[OK] Operación completada.") else log("[ERROR] Código de salida: $code")
            code == 0
        } catch (e: Exception) {
            log("[EXCEPCIÓN] ${e.message}")
            false
        }
    }

    // --- FLASHEAR ARCHIVO (ROM O PARTICIÓN) POR OTG ---
    fun flashOtgFile(
        ctx: Context,
        uri: Uri,
        mode: String, // "sideload", "fastboot_update", "boot", "recovery", "vendor_boot"
        log: (String) -> Unit
    ) {
        val fileName = displayName(ctx, uri)
        val tempFile = File(ctx.cacheDir, fileName)
        log("Copiando $fileName a caché temporal...")
        ctx.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(tempFile).use { output -> input.copyTo(output) }
        } ?: throw IllegalStateException("No se pudo leer el archivo seleccionado")

        when (mode) {
            "sideload" -> {
                log("Iniciando ADB Sideload de Custom ROM...")
                runOtgCommand(ctx, "adb", listOf("sideload", tempFile.absolutePath), log)
            }
            "fastboot_update" -> {
                log("Flasheando ROM Fastboot (.zip)...")
                runOtgCommand(ctx, "fastboot", listOf("update", tempFile.absolutePath), log)
            }
            else -> {
                log("Flasheando imagen en partición '$mode'...")
                runOtgCommand(ctx, "fastboot", listOf("flash", mode, tempFile.absolutePath), log)
            }
        }
        tempFile.delete()
    }

    // --- FLASHEAR KERNEL LOCAL (MODO ROOT) ---
    fun flash(ctx: Context, uri: Uri, doBackup: Boolean, log: (String) -> Unit) {
        val tmp = File(ctx.cacheDir, "kf_${System.currentTimeMillis()}").apply { mkdirs() }
        try {
            val zip = File(tmp, "kernel.zip")
            log("Copiando ZIP local...")
            ctx.contentResolver.openInputStream(uri)!!.use { i -> zip.outputStream().use { i.copyTo(it) } }
            val su = Runtime.getRuntime().exec(arrayOf("su"))
            val out = su.outputStream.bufferedWriter()
            fun sh(s: String) { out.write(s); out.newLine(); out.flush() }
            sh("exec 2>&1")
            if (doBackup) {
                sh("SLOT=$(getprop ro.boot.slot_suffix)")
                sh("BOOT=$(find /dev/block -name \"boot\$SLOT\" 2>/dev/null | head -n1)")
                sh("[ -n \"\$BOOT\" ] && dd if=\"\$BOOT\" of=/sdcard/Download/boot_backup.img bs=4096 && echo 'Backup en /sdcard/Download/boot_backup.img'")
            }
            sh("unzip -oq '${zip.absolutePath}' -d '${tmp.absolutePath}/ak'")
            sh("cd '${tmp.absolutePath}/ak' && sh META-INF/com/google/android/update-binary 3 1 '${zip.absolutePath}'")
            sh("echo DONE:$?; exit")
            var ok = false
            su.inputStream.bufferedReader().forEachLine { line ->
                if (line.startsWith("DONE:")) ok = line.removePrefix("DONE:").trim() == "0"
                else log(line)
            }
            su.waitFor()
            if (!ok) throw RuntimeException("Fallo el flasheo local")
            log("¡Kernel flasheado con éxito!")
        } finally {
            tmp.deleteRecursively()
        }
    }
}
