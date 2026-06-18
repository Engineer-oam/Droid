package com.example.backend

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.DataOutputStream

/**
 * Executes shell commands on the Android system.
 * To properly emulate a USB Mass Storage device, the app requires root (su) access
 * because it needs to modify kernel sysfs/configfs nodes.
 */
object RootBackend {

    /**
     * Checks if the device has root access available.
     */
    suspend fun isRootAvailable(): Boolean = withContext(Dispatchers.IO) {
        try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            val exitValue = process.waitFor()
            exitValue == 0
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Executes a command as the root user.
     */
    suspend fun executeSuCommand(command: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val process = Runtime.getRuntime().exec("su")
            val os = DataOutputStream(process.outputStream)
            
            os.writeBytes("$command\n")
            os.writeBytes("exit\n")
            os.flush()
            
            val exitValue = process.waitFor()
            exitValue == 0
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}

/**
 * Manages the USB Gadget subsystem (ConfigFS or SysFS) to mount payload files.
 * This interacts closely with Android's kernel structure.
 */
object UsbGadgetManager {
    
    // Path varies heavily by device (e.g. /sys/class/android_usb/android0/f_mass_storage/lun/file)
    // ConfigFS is standard on modern Android:
    private const val LUN_PATH_CONFIGFS = "/config/usb_gadget/g1/functions/mass_storage.0/lun.0/file"
    private const val LUN_PATH_SYSFS = "/sys/class/android_usb/android0/f_mass_storage/lun/file"

    suspend fun mountImage(filePath: String): Boolean {
        // Attempt ConfigFS first
        var success = RootBackend.executeSuCommand("echo '$filePath' > $LUN_PATH_CONFIGFS")
        if (!success) {
            // Fallback to legacy SysFS
            success = RootBackend.executeSuCommand("echo '$filePath' > $LUN_PATH_SYSFS")
        }
        return success
    }

    suspend fun unmountImage(): Boolean {
        var success = RootBackend.executeSuCommand("echo '' > $LUN_PATH_CONFIGFS")
        if (!success) {
            success = RootBackend.executeSuCommand("echo '' > $LUN_PATH_SYSFS")
        }
        return success
    }
}
