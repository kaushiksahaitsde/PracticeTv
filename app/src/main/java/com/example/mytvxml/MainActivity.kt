package com.example.mytvxml

import android.content.Intent
import android.os.Bundle
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat.startForegroundService
import androidx.fragment.app.FragmentActivity
import com.example.mytvxml.service.MediaTrackerService

/** Mobile entry point. Handles NLS permission check and delegates tracking to MediaTrackerService. */
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

        refreshPermissionHint()
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionHint()
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

    private fun refreshPermissionHint() {
        txtPermissionHint.text = if (isNotificationListenerEnabled()) {
            "✅ Notification Access: OK — ready to track!"
        } else {
            "⚠️ Notification Access: OFF\nTap Start to enable it."
        }
    }

    private fun startTracking() {
        if (!isNotificationListenerEnabled()) {
            // ACTION_NOTIFICATION_LISTENER_SETTINGS requires API 22; use ACTION_SETTINGS as fallback
            val action = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS
            } else {
                Settings.ACTION_SETTINGS
            }
            try {
                startActivity(Intent(action))
                Toast.makeText(this, "Enable TRP Tracker in Notification Access, then press Start again", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Log.e(TAG, "Could not open NLS settings: ${e.message}")
                Toast.makeText(this, "Go to Settings → Notification Access manually", Toast.LENGTH_LONG).show()
            }
            return
        }

        /*
        if (!isUsageAccessGranted()) {
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
