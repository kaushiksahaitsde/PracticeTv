package com.example.mytvxml

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat.startForegroundService
import androidx.fragment.app.FragmentActivity
import com.bumptech.glide.Glide
import com.example.mytvxml.service.MediaTrackerService
import com.google.gson.Gson
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

/** TV entry point. Hosts the content browser and exposes tracker start/stop controls. */
class MainActivity : FragmentActivity() {



    companion object {
        private const val TAG = "TRP_MainActivity_TV"
    }

    lateinit var txtTitle: TextView
    lateinit var txtSubTitle: TextView
    lateinit var txtDescription: TextView
    lateinit var imgBanner: ImageView
    lateinit var listFragment: ListFragment

    private lateinit var btnStartTracking: Button
    private lateinit var btnStopTracking: Button
    private lateinit var txtTrackingStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        imgBanner      = findViewById(R.id.img_banner)
        txtTitle       = findViewById(R.id.title)
        txtSubTitle    = findViewById(R.id.subtitle)
        txtDescription = findViewById(R.id.description)

        listFragment = ListFragment()
        supportFragmentManager.beginTransaction()
            .add(R.id.list_fragment, listFragment)
            .commit()

        val gson = Gson()
        val i: InputStream = assets.open("movies.json")
        val dataList: DataModel = gson.fromJson(BufferedReader(InputStreamReader(i)), DataModel::class.java)
        listFragment.bindData(dataList)
        listFragment.setOnContentSelectedListener { updateBanner(it) }

        btnStartTracking  = findViewById(R.id.btn_start_tracking)
        btnStopTracking   = findViewById(R.id.btn_stop_tracking)
        txtTrackingStatus = findViewById(R.id.txt_tracking_status)

        btnStartTracking.setOnClickListener {startTracking()   }
        btnStopTracking.setOnClickListener  { stopTracking()  }
    }

    private fun isNotificationListenerEnabled(): Boolean {
        return try {
            val enabled = Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: return false
            enabled.contains("$packageName/com.example.mytvxml.service.TrpNotificationListenerService")
        } catch (e: Exception) {
            Log.e(TAG, "Error checking NLS", e)
            false
        }
    }

    /*
    private fun isUsageAccessGranted(): Boolean {
        return try {
            val appOps = getSystemService(APP_OPS_SERVICE) as AppOpsManager
            val mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName)
            mode == AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) {
            Log.e(TAG, "Error checking usage access", e)
            false
        }
    }
    */

    /**
     * Tries settings intents in priority order. TV ROMs often omit specific settings screens,
     * so we fall back gracefully rather than letting the app crash.
     */
    private fun openNotificationListenerSettings() {
        val intents = listOf(
            Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"),
            Intent(Settings.ACTION_SECURITY_SETTINGS),
            Intent(Settings.ACTION_SETTINGS)
        )
        for (intent in intents) {
            try { startActivity(intent); return } catch (_: Exception) {}
        }
        val adbCmd = "adb shell cmd notification allow_listener " +
                "$packageName/com.example.mytvxml.service.TrpNotificationListenerService"
        Log.e(TAG, "No settings screen. Use ADB:\n$adbCmd")
        Toast.makeText(this, "Enable via ADB:\n$adbCmd", Toast.LENGTH_LONG).show()
    }

    /*
    private fun openUsageAccessSettings() {
        val intents = listOf(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS), Intent(Settings.ACTION_SETTINGS))
        for (intent in intents) {
            try { startActivity(intent); return } catch (_: Exception) {}
        }
        val adbCmd = "adb shell appops set $packageName PACKAGE_USAGE_STATS allow"
        Log.e(TAG, "No usage settings screen. Use ADB:\n$adbCmd")
        Toast.makeText(this, "Enable via ADB:\n$adbCmd", Toast.LENGTH_LONG).show()
    }
    */


    private fun tryEnableNotificationListenerViaShell():Boolean{
        val component = "$packageName/com.example.mytvxml.service.TrpNotificationListenerService"
       // val cmd = "cmd notification allow_listener $component"
        val cmd= "getprop"
        // root first try

        val suResult=runShellCommand(cmd, useSu = true)
        Log.i(TAG, "allow_listener via su -> exit=${suResult.exitCode}, err=${suResult.stderr}, out=${suResult.stdout}")
        if (suResult.exitCode == 0 && isNotificationListenerEnabled()) return true

        // 2) Try non-root shell
        val shResult = runShellCommand(cmd, useSu = false)
        Log.i(TAG, "allow_listener via sh -> exit=${shResult.exitCode}, err=${shResult.stderr}, out=${shResult.stdout}")
        return shResult.exitCode == 0 && isNotificationListenerEnabled()
    }

    private fun runShellCommand(command: String, useSu: Boolean): ShellResult{

        val fullCmd= if(useSu) arrayOf("su","-c",command) else arrayOf("sh", "-c", command)

        return try{
            val process= Runtime.getRuntime().exec(fullCmd)

            val watcher = Thread {
                try {
                    Thread.sleep(5000)
                    process.destroy()
                } catch (_: Throwable) {
                }
            }
            watcher.isDaemon = true
            watcher.start()

            val exitCode = process.waitFor()
            ShellResult(
                exitCode = exitCode,
                stdout = BufferedReader(InputStreamReader(process.inputStream)).use { it.readText() }.trim(),
                stderr = BufferedReader(InputStreamReader(process.errorStream)).use { it.readText() }.trim(),
                command = (if (useSu) "su -c " else "sh -c ") + command
            )

        } catch (t: Throwable){
            ShellResult(
                exitCode = -1,
                stdout = "",
                stderr = "${t.javaClass.simpleName}: ${t.message}",
                command = (if (useSu) "su -c " else "sh -c ") + command
            )
        }
    }

    private fun startTracking() {
        if (!isNotificationListenerEnabled()) {
            openNotificationListenerSettings()
            Toast.makeText(this, "Enable Notification Access, then press Start again", Toast.LENGTH_LONG).show()
            return
        }

        // Usage Access check disabled for now — only NLS matters for this R&D phase.
        // Uncomment when foreground-app detection is needed:
        /*
        if (!isUsageAccessGranted()) {
            openUsageAccessSettings()
            Toast.makeText(this, "Enable Usage Access, then press Start again", Toast.LENGTH_LONG).show()
            return
        }
        */

        try {
            startForegroundService(this, Intent(this, MediaTrackerService::class.java))
            txtTrackingStatus.text = getString(R.string.tracking_on)
            Log.i(TAG, "MediaTrackerService started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start tracker", e)
            Toast.makeText(this, "Could not start tracker: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun stopTracking() {
        try {
            stopService(Intent(this, MediaTrackerService::class.java))
            txtTrackingStatus.text = getString(R.string.tracking_off)
            Log.i(TAG, "MediaTrackerService stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop tracker", e)
        }
    }

    fun updateBanner(detail: DataModel.Result.Detail) {
        txtTitle.text       = detail.title
        txtDescription.text = detail.overview
        Glide.with(this)
            .load("https://www.themoviedb.org/t/p/w780${detail.backdrop_path}")
            .into(imgBanner)
    }
}

