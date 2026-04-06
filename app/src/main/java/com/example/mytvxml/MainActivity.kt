package com.example.mytvxml

// import android.app.AppOpsManager   // commented — Usage Access check disabled for now
import android.content.Intent
import android.os.Bundle
import android.os.Build
// import android.os.Process           // commented — Usage Access check disabled for now
import android.provider.Settings
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat.startForegroundService
import androidx.fragment.app.FragmentActivity
import com.example.mytvxml.service.MediaTrackerService

/**
 * Mobile MainActivity — simple 2-button TRP tracking UI.
 *
 * Mobile advantage over TV:
 *   • Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS IS available on phones.
 *   • User can self-enable Notification Access directly from the app, no ADB.
 *   • Settings.ACTION_USAGE_ACCESS_SETTINGS also works on phones.
 *
 * Flow:
 *   1. User taps "Start Tracking"
 *   2. If Notification Access not granted → opens Settings directly (works on phone!)
 *   3. User enables it, comes back, taps Start again
 *   4. If Usage Access not granted → opens Settings
 *   5. Both granted → MediaTrackerService starts (same tracker as TV)
 */
class MainActivity : FragmentActivity() {

    companion object {
        private const val TAG = "TRP_MainActivity_Mobile"
    }

    private lateinit var btnStart: Button
    private lateinit var btnStop: Button
    private lateinit var txtStatus: TextView
    private lateinit var txtPermissionHint: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnStart          = findViewById(R.id.btn_start_tracking)
        btnStop           = findViewById(R.id.btn_stop_tracking)
        txtStatus         = findViewById(R.id.txt_tracking_status)
        txtPermissionHint = findViewById(R.id.txt_permission_hint)

        btnStart.setOnClickListener { startTracking() }
        btnStop.setOnClickListener  { stopTracking()  }

        // Show current permission state on launch
        refreshPermissionHint()
    }

    override fun onResume() {
        super.onResume()
        // Re-check when user comes back from Settings
        refreshPermissionHint()
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

    private fun refreshPermissionHint() {
        val nlsOk = isNotificationListenerEnabled()
        txtPermissionHint.text = if (nlsOk) {
            "✅ Notification Access: OK — ready to track!"
        } else {
            "⚠️ Notification Access: OFF\nTap Start to enable it."
        }
    }

    // ─────────────────────────────────────────────────────────
    // TRACKING CONTROL
    // ─────────────────────────────────────────────────────────

    private fun startTracking() {
        // STEP 1: Notification Listener (needed for MediaSession reads + notif token extraction)
        if (!isNotificationListenerEnabled()) {
            Log.w(TAG, "NLS not enabled — opening Notification Listener Settings")
            // ACTION_NOTIFICATION_LISTENER_SETTINGS available from API 22.
            // On phone (minSdk 21), guard it — API 21 devices are extremely rare today.
            val nlsAction = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS
            } else {
                Settings.ACTION_SETTINGS  // fallback for API 21
            }
            try {
                startActivity(Intent(nlsAction))
                Toast.makeText(
                    this,
                    "Enable TRP Tracker in Notification Access, then press Start again",
                    Toast.LENGTH_LONG
                ).show()
            } catch (e: Exception) {
                Log.e(TAG, "Could not open NLS settings: ${e.message}")
                Toast.makeText(this, "Go to Settings → Notification Access manually", Toast.LENGTH_LONG).show()
            }
            return
        }

        // STEP 2 (Usage Access) — disabled for now, not needed for NLS tracking.
        // Uncomment when foreground-app detection is needed:
        /*
        if (!isUsageAccessGranted()) {
            Log.w(TAG, "Usage access not granted — opening Usage Access Settings")
            try {
                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                Toast.makeText(this, "Enable Usage Access for TRP Tracker, then press Start again", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Log.e(TAG, "Could not open usage settings: ${e.message}")
                Toast.makeText(this, "Go to Settings → Usage Access manually", Toast.LENGTH_LONG).show()
            }
            return
        }
        */

        // NLS granted — start the tracker service
        try {
            startForegroundService(this, Intent(this, MediaTrackerService::class.java))
            txtStatus.text = getString(R.string.tracking_on)
            refreshPermissionHint()
            Log.i(TAG, "MediaTrackerService started")
            Toast.makeText(this, "TRP Tracking started — check Logcat for data", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start tracker", e)
            Toast.makeText(this, "Could not start tracker: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun stopTracking() {
        try {
            stopService(Intent(this, MediaTrackerService::class.java))
            txtStatus.text = getString(R.string.tracking_off)
            Log.i(TAG, "MediaTrackerService stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop tracker", e)
        }
    }
}
