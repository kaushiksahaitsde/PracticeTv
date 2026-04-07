package com.example.core

import android.content.Context
import android.util.Log
import dadb.AdbKeyPair
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import dadb.Dadb
import kotlinx.coroutines.delay
import java.io.File

object AdbManager {

    const val TAG="AdbManager"
    private const val LISTENER_COMPONENT =
        "com.example.mytvxml.tv/com.example.mytvxml.service.TrpNotificationListenerService"

    suspend fun grantNotificationListener(
        context: Context,
        ip: String,
        adbPort: Int = 5555
    ): Result<String> = withContext(Dispatchers.IO) {
        try{
            val keyDir= File(context.filesDir,"adb_keys")
            if(!keyDir.exists()) keyDir.mkdirs()

            val privateKeyFile=File(keyDir,"adbkey")
            val publicKeyFile=File(keyDir,"adbkey.pub")
            val keyPair: AdbKeyPair? = (if(privateKeyFile.exists() && publicKeyFile.exists()){
                 Log.i(TAG, "Reusing existing ADB key pair")
                 AdbKeyPair.read(privateKeyFile, publicKeyFile)
             }else{
                 Log.i(TAG, "Generating new ADB key pair")
                 AdbKeyPair.generate(privateKeyFile,publicKeyFile)
             }) as AdbKeyPair?

            Dadb.create(ip,adbPort, keyPair ).use{ adb->

                val resultGrant= adb.shell("cmd notification allow_listener $LISTENER_COMPONENT")

                Log.i(TAG, "Grant output: ${resultGrant.allOutput}")

                delay(5000)
                val verifyRaw=adb.shell("settings get secure enabled_notification_listeners").output

                Log.i(TAG,"verifyRaw=$verifyRaw")
                if(!verifyRaw.contains("com.example.mytvxml.tv")){
                    return@withContext Result.failure(
                        Exception("Grant sent but permission not confirmed. Please try again.")
                    )
                }
            }
            Result.success("Notification listener enabled successfully!")
        } catch (e: Exception){
            Log.e(TAG,"Failed to connect to FireStick: ${e.message}", e)
            Result.failure(Exception("Failed to connect to FireStick: ${e.message}", e))
        }

    }
}