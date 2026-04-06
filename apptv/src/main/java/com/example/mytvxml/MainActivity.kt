package com.example.mytvxml

// import android.app.AppOpsManager   // commented — Usage Access check disabled for now
import android.content.Intent
import android.os.Bundle
// import android.os.Process           // commented — Usage Access check disabled for now
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

/**
 * Android TV MainActivity — movie browser + TRP tracking controls.
 *
 * TV-specific notes:
 *   • Uses FragmentActivity (Leanback-compatible, no ActionBar)
 *   • Buttons are D-pad navigable (focusable, focusableInTouchMode)
 *   • Settings redirect is crash-safe (many TV ROMs lack certain settings screens)
 *   • If TV has no Notification Access settings screen → shows ADB fallback
 */
class MainActivity : FragmentActivity() {

    companion object {
        private const val TAG = "TRP_MainActivity_TV"
    }

    // ── TV UI views ──────────────────────────────────────────
    lateinit var txtTitle: TextView
    lateinit var txtSubTitle: TextView
    lateinit var txtDescription: TextView
    lateinit var imgBanner: ImageView
    lateinit var listFragment: ListFragment

    // ── Tracking controls ────────────────────────────────────
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
            val enabled = Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: return false
            enabled.contains("$packageName/com.example.mytvxml.service.TrpNotificationListenerService")
        } catch (e: Exception) {
            Log.e(TAG, "Error checking NLS", e)
            false
        }
    }

    // Usage Access check disabled for now — only NLS matters for this R&D phase.
    // Uncomment when foreground-app detection (checkForegroundApp) needs to be tested.
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

    // ─────────────────────────────────────────────────────────
    // CRASH-SAFE SETTINGS NAVIGATION
    // Many TV ROMs don't have standard settings screens.
    // We try multiple intents in order and never crash.
    // ─────────────────────────────────────────────────────────

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

    // Usage Access settings navigation disabled for now.
    // Uncomment when foreground-app detection is needed:
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

    // ─────────────────────────────────────────────────────────
    // TRACKING CONTROL
    // ─────────────────────────────────────────────────────────

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

    // ─────────────────────────────────────────────────────────
    // BANNER
    // ─────────────────────────────────────────────────────────

    fun updateBanner(detail: DataModel.Result.Detail) {
        txtTitle.text       = detail.title
        txtDescription.text = detail.overview
        Glide.with(this)
            .load("https://www.themoviedb.org/t/p/w780${detail.backdrop_path}")
            .into(imgBanner)
    }
}
