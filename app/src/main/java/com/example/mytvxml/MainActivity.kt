package com.example.mytvxml

import android.app.AppOpsManager
import android.content.Intent
import android.os.Bundle
import android.os.Process
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

class MainActivity : FragmentActivity() {

    companion object {
        private const val TAG = "TRP_MainActivity"
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

        btnStartTracking.setOnClickListener { startTracking() }
        btnStopTracking.setOnClickListener  { stopTracking()  }
    }

    // ─────────────────────────────────────────────────────────
    // PERMISSION CHECKS
    // ─────────────────────────────────────────────────────────

    private fun isNotificationListenerEnabled(): Boolean {
        return try {
            val enabled = Settings.Secure.getString(
                contentResolver, "enabled_notification_listeners"
            ) ?: return false
            enabled.contains(
                "$packageName/com.example.mytvxml.service.TrpNotificationListenerService"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error checking notification listener", e)
            false
        }
    }

    private fun isUsageAccessGranted(): Boolean {
        return try {
            val appOps = getSystemService(APP_OPS_SERVICE) as AppOpsManager
            val mode = appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                packageName
            )
            mode == AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) {
            Log.e(TAG, "Error checking usage access", e)
            false
        }
    }

    // ─────────────────────────────────────────────────────────
    // SAFE SETTINGS NAVIGATION
    //
    // TV devices often don't have standard settings screens.
    // We try multiple intents in order — each wrapped in try-catch
    // so the app NEVER crashes regardless of what the TV has.
    // ─────────────────────────────────────────────────────────

    /**
     * Opens Notification Listener settings so the user can enable our app.
     *
     * If the TV doesn't have this settings screen at all, we log the
     * equivalent ADB command that does the same thing.
     *
     * ADB equivalent:
     *   adb shell cmd notification allow_listener \
     *     com.example.mytvxml/com.example.mytvxml.service.TrpNotificationListenerService
     */
    private fun openNotificationListenerSettings() {
        val intents = listOf(
            Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"),
            Intent(Settings.ACTION_SECURITY_SETTINGS),
            Intent(Settings.ACTION_SETTINGS)
        )
        for (intent in intents) {
            try {
                startActivity(intent)
                Log.d(TAG, "Opened settings: ${intent.action}")
                return
            } catch (_: Exception) {
                Log.w(TAG, "Settings screen not found: ${intent.action}")
            }
        }
        // All settings screens failed — show ADB fallback instruction
        val adbCmd = "adb shell cmd notification allow_listener " +
                "$packageName/com.example.mytvxml.service.TrpNotificationListenerService"
        Log.e(TAG, "No settings screen available. Use ADB:\n$adbCmd")
        Toast.makeText(this, "Enable via ADB:\n$adbCmd", Toast.LENGTH_LONG).show()
    }

    /**
     * Opens Usage Access settings.
     *
     * ADB equivalent:
     *   adb shell appops set com.example.mytvxml PACKAGE_USAGE_STATS allow
     */
    private fun openUsageAccessSettings() {
        val intents = listOf(
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS),
            Intent(Settings.ACTION_SETTINGS)
        )
        for (intent in intents) {
            try {
                startActivity(intent)
                Log.d(TAG, "Opened usage settings: ${intent.action}")
                return
            } catch (_: Exception) {
                Log.w(TAG, "Settings screen not found: ${intent.action}")
            }
        }
        val adbCmd = "adb shell appops set $packageName PACKAGE_USAGE_STATS allow"
        Log.e(TAG, "No usage settings screen available. Use ADB:\n$adbCmd")
        Toast.makeText(this, "Enable via ADB:\n$adbCmd", Toast.LENGTH_LONG).show()
    }

    // ─────────────────────────────────────────────────────────
    // TRACKING CONTROL
    // ─────────────────────────────────────────────────────────

    /**
     * Starts the TRP tracking service.
     *
     * Permission flow:
     *   1. Check if Notification Listener is enabled (needed for MediaSession access).
     *      If not → open Settings so user can enable it, then they press Start again.
     *      (The service itself will still try a direct MEDIA_CONTENT_CONTROL fallback.)
     *
     *   2. Check if Usage Stats is granted (needed for foreground-app detection).
     *      If not → open Settings, then press Start again.
     *
     *   3. Both OK → start MediaTrackerService.
     */
    private fun startTracking() {
        if (!isNotificationListenerEnabled()) {
            Log.w(TAG, "NLS not enabled — opening settings")
            openNotificationListenerSettings()
            Toast.makeText(
                this,
                "Enable Notification Access for TRP Tracker, then press Start again",
                Toast.LENGTH_LONG
            ).show()
            return
        }

       /* if (!isUsageAccessGranted()) {
            Log.w(TAG, "Usage access not granted — opening settings")
            openUsageAccessSettings()
            Toast.makeText(
                this,
                "Enable Usage Access for TRP Tracker, then press Start again",
                Toast.LENGTH_LONG
            ).show()
            return
        }
*/
        try {
            val serviceIntent = Intent(this, MediaTrackerService::class.java)
            startForegroundService(this, serviceIntent)
            txtTrackingStatus.text = getString(R.string.tracking_on)
            Log.i(TAG, "MediaTrackerService started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start MediaTrackerService", e)
            Toast.makeText(this, "Could not start tracker: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun stopTracking() {
        try {
            stopService(Intent(this, MediaTrackerService::class.java))
            txtTrackingStatus.text = getString(R.string.tracking_off)
            Log.i(TAG, "MediaTrackerService stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop MediaTrackerService", e)
        }
    }

    // ─────────────────────────────────────────────────────────
    // UI HELPERS
    // ─────────────────────────────────────────────────────────

    fun updateBanner(dataList: DataModel.Result.Detail) {
        txtTitle.text       = dataList.title
        txtDescription.text = dataList.overview
        Glide.with(this)
            .load("https://www.themoviedb.org/t/p/w780${dataList.backdrop_path}")
            .into(imgBanner)
    }
}
