/*
 * Copyright © 2020-2021 Jamal Rothfuchs
 * Copyright © 2020-2021 Stéphane Lenclud
 * Copyright © 2015 Anthony Restaino
 */

package com.jamal2367.styx.utils

import android.app.Application
import android.os.Bundle
import android.os.Environment
import android.os.Parcel
import android.util.Log
import androidx.annotation.NonNull
import com.jamal2367.styx.BrowserApp
import com.jamal2367.styx.utils.Utils.close
import java.io.*


/**
 * A utility class containing helpful methods
 * pertaining to file storage.
 */
@Suppress("DEPRECATION")
object FileUtils {
    private const val TAG = "FileUtils"
    val DEFAULT_DOWNLOAD_PATH: String =
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).path
    private const val BACKUP_SUFFIX = ".backup"

    /**
     * Writes a bundle to persistent storage in the files directory
     * using the specified file name. This method is a blocking
     * operation.
     *
     * @param app    the application needed to obtain the file directory.
     * @param bundle the bundle to store in persistent storage.
     * @param name   the name of the file to store the bundle in.
     */
    fun writeBundleToStorage(@NonNull app: Application, bundle: Bundle?, @NonNull name: String) {
        val outputFile = File(app.filesDir, name)
        var outputStream: FileOutputStream? = null
        try {
            // if file exists, rename to name.backup
            val backupFile = File(outputFile.absolutePath + BACKUP_SUFFIX)
            if (outputFile.exists() && backupFile.exists()) backupFile.delete() // need to delete old backup file before renaming?
            if (outputFile.exists()) outputFile.renameTo(backupFile)
            outputStream = FileOutputStream(outputFile)
            val parcel = Parcel.obtain()
            parcel.writeBundle(bundle)
            outputStream.write(parcel.marshall())
            outputStream.flush()
            parcel.recycle()
        } catch (e: IOException) {
            Log.e(TAG, "Unable to write bundle to storage")
        } finally {
            close(outputStream)
        }
    }

    /**
     * Use this method to delete the bundle with the specified name.
     * This is a blocking call and should be used within a worker
     * thread unless immediate deletion is necessary.
     *
     * @param app  the application object needed to get the file.
     * @param name the name of the file.
     */
    fun deleteBundleInStorage(app: Application, name: String) {
        val outputFile = File(app.filesDir, name)
        if (outputFile.exists()) {
            outputFile.delete()
        }

        // we might have a backup file, needs to be deleted too, or it might get read accidentally
        val backupFile = File(app.filesDir, name + BACKUP_SUFFIX)
        if (backupFile.exists()) {
            backupFile.delete()
        }
    }

    /**
     * Rename a file from given application storage.
     *
     * @param app  the application object needed to get the file.
     * @param name the name of the file to rename.
     * @param aNewName New file name.
     */
    fun renameBundleInStorage(app: Application, name: String, aNewName: String) {
        val srcFile = File(app.filesDir, name)
        if (srcFile.exists()) {
            val destFile = File(app.filesDir, aNewName)
            srcFile.renameTo(destFile)
        }

        val srcBackupFile = File(app.filesDir, name + BACKUP_SUFFIX)
        if (srcBackupFile.exists()) {
            val destBackupFile = File(app.filesDir, aNewName + BACKUP_SUFFIX)
            srcBackupFile.renameTo(destBackupFile)
        }
    }

    /**
     * Reads a bundle from the file with the specified
     * name in the peristent storage files directory.
     * This method is a blocking operation.
     *
     * @param app  the application needed to obtain the files directory.
     * @param name the name of the file to read from.
     * @return a valid Bundle loaded using the system class loader
     * or null if the method was unable to read the Bundle from storage.
     */
    fun readBundleFromStorage(app: Application, name: String): Bundle? {
        val inputFile = File(app.filesDir, name)
        var inputStream: FileInputStream? = null
        try {
            inputStream = FileInputStream(inputFile)
            val parcel = Parcel.obtain()
            val data = ByteArray(inputStream.channel.size().toInt())
            inputStream.read(data, 0, data.size)
            parcel.unmarshall(data, 0, data.size)
            parcel.setDataPosition(0)
            val out = parcel.readBundle(app.classLoader)
            out!!.putAll(out)
            parcel.recycle()
            return out
        } catch (e: FileNotFoundException) {
            Log.e(TAG, "Unable to read bundle from storage")
            // try backup file, but make sure we don't end up in a loop
            if (!name.endsWith(BACKUP_SUFFIX))
                readBundleFromStorage(app, name + BACKUP_SUFFIX)
        } catch (e: IOException) {
            Log.e(TAG, "Unable to read bundle from storage", e)
            // try backup file, but make sure we don't end up in a loop
            if (!name.endsWith(BACKUP_SUFFIX))
                readBundleFromStorage(app, name + BACKUP_SUFFIX)
        } finally {
            close(inputStream)
        }
        return null
    }

    /**
     * Writes a stacktrace to the downloads folder with
     * the following filename: EXCEPTION_[TIME OF CRASH IN MILLIS].txt
     *
     * @param throwable the Throwable to log to external storage
     */
    fun writeCrashToStorage(throwable: Throwable) {
        val fileName = throwable.javaClass.simpleName + '_' + System.currentTimeMillis() + ".txt"
        val outputFile =
            File(BrowserApp.instance.applicationContext.getExternalFilesDir("CrashLogs"), fileName)
        var outputStream: FileOutputStream? = null
        try {
            outputStream = FileOutputStream(outputFile)
            throwable.printStackTrace(PrintStream(outputStream))
            outputStream.flush()
        } catch (e: IOException) {
            Log.e(TAG, "Unable to write bundle to storage")
        } finally {
            close(outputStream)
        }
    }

    /**
     * Converts megabytes to bytes.
     *
     * @param megaBytes the number of megabytes.
     * @return the converted bytes.
     */
    fun megabytesToBytes(megaBytes: Long): Long {
        return megaBytes * 1024 * 1024
    }

    fun addNecessarySlashes(originalPath: String?): String {
        var originalPaths = originalPath
        if (originalPaths == null || originalPaths.isEmpty()) {
            return "/"
        }
        if (originalPaths[originalPaths.length - 1] != '/') {
            originalPaths = "$originalPath/"
        }
        if (originalPaths[0] != '/') {
            originalPaths = "/$originalPath"
        }
        return originalPaths
    }
}