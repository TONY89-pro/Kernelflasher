package com.kernelflasher

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.util.zip.ZipFile

object Flasher {
    private fun su(cmd: String): Process =
        ProcessBuilder("su", "-c", cmd).redirectErrorStream(true).start()

    fun hasRoot(): Boolean = runCatching {
        val p = su("id")
        val out = p.inputStream.bufferedReader().readText()
        p.waitFor()
        out.contains("uid=0")
    }.getOrDefault(false)

    fun displayName(ctx: Context, uri: Uri): String =
        ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (c.moveToFirst() && i >= 0) c.getString(i) else null
        } ?: "kernel.zip"

    /** Runs a root command and forwards its output line by line. */
    private fun runLines(cmd: String, log: (String) -> Unit): Int {
        val p = su(cmd)
        p.inputStream.bufferedReader().forEachLine { raw ->
            val line = if (raw.startsWith("ui_print ")) raw.removePrefix("ui_print ") else raw
            if (!line.startsWith("progress ") && !line.startsWith("set_progress")) log(line)
        }
        return p.waitFor()
    }

    private const val BACKUP_CMD =
        "SLOT=\$(getprop ro.boot.slot_suffix); DEV=/dev/block/by-name/boot\$SLOT; " +
        "[ -e \$DEV ] || { echo \"No se encontro \$DEV\"; exit 5; }; " +
        "OUT=/sdcard/Download/boot_backup_\$(date +%Y%m%d_%H%M%S).img; " +
        "dd if=\$DEV of=\$OUT && echo \"Backup guardado en \$OUT\""

    /** Returns 0 on success. */
    fun flash(ctx: Context, uri: Uri, backup: Boolean, log: (String) -> Unit): Int {
        val work = File(ctx.cacheDir, "kf")
        work.deleteRecursively(); work.mkdirs()
        val zip = File(work, "kernel.zip")

        log("Copiando ZIP...")
        val input = ctx.contentResolver.openInputStream(uri)
        if (input == null) { log("[ERROR] No se pudo abrir el archivo."); return 2 }
        input.use { i -> zip.outputStream().use { o -> i.copyTo(o) } }

        val ub = File(work, "update-binary")
        ZipFile(zip).use { zf ->
            if (zf.getEntry("anykernel.sh") == null) {
                log("[ERROR] No es un ZIP AnyKernel3 (falta anykernel.sh)."); return 3
            }
            val e = zf.getEntry("META-INF/com/google/android/update-binary")
            if (e == null) { log("[ERROR] Falta update-binary dentro del ZIP."); return 3 }
            zf.getInputStream(e).use { i -> ub.outputStream().use { o -> i.copyTo(o) } }
        }

        if (backup) {
            log("Haciendo backup de boot...")
            if (runLines(BACKUP_CMD, log) != 0) {
                log("[ERROR] El backup fallo. Se cancela el flasheo."); return 4
            }
        }

        val w = work.absolutePath
        val cmd = "cd $w && mkdir -p tmp/anykernel && chmod 755 update-binary && " +
            "AKHOME=$w/tmp/anykernel " +
            "PATH=/data/adb/magisk:/data/adb/ksu/bin:/data/adb/ap/bin:\$PATH " +
            "sh update-binary 3 1 $w/kernel.zip"
        log("Instalando kernel...")
        val rc = runLines(cmd, log)
        log(if (rc == 0) "=== Listo. Reinicia el telefono. ===" else "[ERROR] El instalador termino con codigo $rc")
        return rc
    }
}
