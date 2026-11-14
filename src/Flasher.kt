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
import java.util.concurrent.TimeUnit

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
                } catch (e: Exception) {
                    log("[AVISO] Binario $assetName no encontrado en assets.")
                }
            }
            if (destFile.exists()) destFile.setExecutable(true, false)
        }
        return Pair(fastbootFile, adbFile)
    }

    private fun resolveTool(ctx: Context, tool: String, log: (String) -> Unit): File? {
        val (fb, adb) = prepareBinaries(ctx, log)
        val f = if (tool == "fastboot") fb else adb
        return if (f.exists() && f.length() > 0L) f else null
    }

    private fun baseEnv(ctx: Context) = mapOf(
        "HOME" to ctx.filesDir.absolutePath,
        "TMPDIR" to ctx.cacheDir.absolutePath
    )

    // --- EJECUTAR UN COMANDO Y DEVOLVER SU SALIDA (sin escribir nada en el log) ---
    // La salida va a un archivo: así no se cuelga cuando el daemon de adb hereda el pipe.
    data class CmdResult(val code: Int, val out: String, val timedOut: Boolean)

    fun runCapture(ctx: Context, tool: String, args: List<String>, timeoutSec: Long = 8): CmdResult {
        val bin = resolveTool(ctx, tool) {} ?: return CmdResult(-2, "", false)
        val outFile = File.createTempFile("cmd", ".log", ctx.cacheDir)
        return try {
            val pb = ProcessBuilder(listOf(bin.absolutePath) + args)
                .redirectErrorStream(true)
                .redirectOutput(outFile)
            pb.environment().putAll(baseEnv(ctx))
            val p = pb.start()
            val finished = p.waitFor(timeoutSec, TimeUnit.SECONDS)
            if (!finished) p.destroyForcibly()
            CmdResult(if (finished) p.exitValue() else -1, outFile.readText(), !finished)
        } catch (e: Exception) {
            CmdResult(-3, e.message ?: "", false)
        } finally {
            outFile.delete()
        }
    }

    // --- DETECCIÓN REAL: solo cuenta "serial + estado válido" ---
    private fun parseDevices(out: String): List<Pair<String, String>> =
        out.lineSequence().map { it.trim() }
            .filter {
                it.isNotEmpty() && !it.startsWith("*") && !it.startsWith("List of devices") &&
                    !it.startsWith("error") && !it.startsWith("adb server")
            }
            .mapNotNull { l ->
                l.split(Regex("\\s+")).let { if (it.size >= 2) it[0] to it[1] else null }
            }
            .toList()

    fun adbOnline(out: String) = parseDevices(out).any { it.second == "device" }
    fun fastbootOnline(out: String) = parseDevices(out).any { it.second.startsWith("fastboot") }

    /** Devuelve (adbOnline, fastbootOnline). Sin cable OTG ni se ejecutan los comandos. */
    fun scanDevices(ctx: Context): Pair<Boolean, Boolean> {
        if (getConnectedUsbDevices(ctx).isEmpty()) return false to false
        runCapture(ctx, "adb", listOf("start-server"), 10)
        val a = runCapture(ctx, "adb", listOf("devices"))
        val f = runCapture(ctx, "fastboot", listOf("devices"))
        return (a.code == 0 && adbOnline(a.out)) to (f.code == 0 && fastbootOnline(f.out))
    }

    // --- EJECUTAR COMANDO OTG (FASTBOOT / ADB) CON SALIDA EN VIVO ---
    fun runOtgCommand(
        ctx: Context,
        tool: String, // "fastboot" o "adb"
        args: List<String>,
        log: (String) -> Unit
    ): Boolean {
        if (getConnectedUsbDevices(ctx).isEmpty()) {
            log("[ERROR] No hay ningún dispositivo conectado por OTG.")
            return false
        }
        val exe = resolveTool(ctx, tool, log)
        if (exe == null) {
            log("[ERROR] No se encontró el binario '$tool' en assets.")
            return false
        }
        if (!exe.canExecute()) {
            log("[ERROR] '$tool' no se puede ejecutar en este Android (permiso denegado).")
            return false
        }
        // Arranca el daemon de adb aparte para que no deje colgado el pipe de abajo.
        if (tool == "adb") runCapture(ctx, "adb", listOf("start-server"), 10)

        log("> Ejecutando: $tool ${args.joinToString(" ")}")
        return try {
            val pb = ProcessBuilder(listOf(exe.absolutePath) + args)
            pb.environment().putAll(baseEnv(ctx))
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
