package com.example.mytvxml.service

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat

/**
 * MediaTrackerService — Foreground service that tracks what other apps are playing.
 *
 * ════════════════════════════════════════════════════════
 * HOW IT READS MEDIA SESSIONS — TWO APPROACHES (both tried)
 * ════════════════════════════════════════════════════════
 *
 * APPROACH 1 — NotificationListenerService (NLS)  -- FIRST CHOICE
 * ─────────────────────────────────────────────────────────
 * We pass our TrpNotificationListenerService ComponentName to
 * getActiveSessions(). Android gives us full access IF:
 *   • The user has gone to Settings → Notification Access → enabled our app
 *     (end-user can do this themselves — no ADB needed)
 *
 *   OR (on TVs where that settings screen doesn't exist):
 *   • adb shell cmd notification allow_listener \
 *       com.example.mytvxml/com.example.mytvxml.service.TrpNotificationListenerService
 *
 * APPROACH 2 — Direct null call  [AUTOMATIC FALLBACK]
 * ─────────────────────────────────────────────────────────
 * If Approach 1 throws a SecurityException (NLS not enabled), we
 * automatically try getActiveSessions(null).
 * This requires the MEDIA_CONTENT_CONTROL system permission, granted via:
 *   adb shell pm grant com.example.mytvxml android.permission.MEDIA_CONTENT_CONTROL
 *
 * ════════════════════════════════════════════════════════
 * TRACKING STRATEGY — both run in parallel
 * ════════════════════════════════════════════════════════
 *   A. POLLING    — scans every 5 seconds (safety net)
 *   B. CALLBACKS  — fires instantly on any play/pause/title change
 */
class MediaTrackerService : Service() {

    companion object {
        private const val TAG = "TRP_Tracker"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "trp_tracker_channel"
        private const val POLL_INTERVAL_MS = 5000L

        // OTT package names — used only for foreground-app filter
        // to avoid spamming logs with launcher/home-screen events.
        val OTT_PACKAGES = setOf(
            "in.startv.hotstar",
            "com.hotstar.android",
            "com.google.android.youtube",
            "com.google.android.youtube.tv",
            "com.google.android.youtube.tvkids",
            "com.amazon.avod",
            "com.netflix.ninja",
            "com.netflix.mediaclient",
            "com.sonyliv",
            "com.jio.media.jiobeats",
            "com.jio.jioplay.tv",
            "com.tv.v18.viola",
            "com.graymatrix.did",
            "com.erosnow",
            "com.mxplayer.android",
            "com.spotify.tv.android",
            "com.apple.atve.androidtv.appletv",
        )
    }

    // ─────────────────────────────────────────────────────────
    // FIELDS
    // ─────────────────────────────────────────────────────────

    private val handler = Handler(Looper.getMainLooper())
    private var mediaSessionManager: MediaSessionManager? = null

    // Key = package name → prevents registering the same callback twice
    private val registeredCallbacks = mutableMapOf<String, MediaController.Callback>()

    // Fires the moment a new app starts/stops a media session
    private var sessionsChangedListener: MediaSessionManager.OnActiveSessionsChangedListener? = null

    // ─────────────────────────────────────────────────────────
    // SERVICE LIFECYCLE
    // ─────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "═══════════════════════════════════════")
        Log.i(TAG, "  TRP TRACKER SERVICE CREATED")
        Log.i(TAG, "═══════════════════════════════════════")

        startForegroundNotification()                          // must happen within 5s

        mediaSessionManager = getSystemService(MEDIA_SESSION_SERVICE) as? MediaSessionManager
        if (mediaSessionManager == null) {
            Log.e(TAG, "❌ FATAL: MediaSessionManager unavailable — stopping service")
            stopSelf()
            return
        }

        setupSessionChangeListener()   // instant notification on session list changes
        startPolling()                 // 5-second safety-net poll
        scanActiveSessions()           // immediate first scan
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "onStartCommand — service running")
        return START_STICKY  // auto-restart if killed due to low memory
    }

    override fun onDestroy() {
        Log.i(TAG, "═══════════════════════════════════════")
        Log.i(TAG, "  TRP TRACKER SERVICE STOPPED")
        Log.i(TAG, "═══════════════════════════════════════")
        handler.removeCallbacksAndMessages(null)
        sessionsChangedListener?.let {
            mediaSessionManager?.removeOnActiveSessionsChangedListener(it)
        }
        registeredCallbacks.clear()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ─────────────────────────────────────────────────────────
    // CORE: GET ACTIVE MEDIA CONTROLLERS
    //
    // This is the hybrid function that tries NLS first,
    // then automatically falls back to the null/direct approach.
    // ─────────────────────────────────────────────────────────

    /**
     * Returns the list of currently active MediaControllers.
     *
     * STEP 1 — Try via NotificationListenerService (NLS)
     *   Requires user to enable "Notification Access" for our app in Settings,
     *   OR: adb shell cmd notification allow_listener \
     *         com.example.mytvxml/com.example.mytvxml.service.TrpNotificationListenerService
     *
     * STEP 2 — Fallback: Try via null (direct MEDIA_CONTENT_CONTROL)
     *   Only runs if Step 1 throws SecurityException (NLS not enabled).
     *   Requires: adb shell pm grant com.example.mytvxml android.permission.MEDIA_CONTENT_CONTROL
     *
     * STEP 3 — Both failed → return empty list (service stays alive, retries next poll)
     */
    private fun getActiveControllers(): List<MediaController> {

        // ── APPROACH 1: NotificationListenerService ──────────
        try {
            val nlsComponent = ComponentName(
                this,
                TrpNotificationListenerService::class.java
            )
            val controllers = mediaSessionManager?.getActiveSessions(nlsComponent)
            if (controllers != null) {
                Log.d(TAG, "✅ [NLS] Got ${controllers.size} session(s) via NotificationListenerService")
                return controllers
            }
        } catch (_: SecurityException) {
            // NLS not enabled by user — this is expected before permission is granted
            Log.w(TAG, "⚠️ [NLS] NotificationListenerService not enabled.")
            Log.w(TAG, "   Option A (user): Settings → Notification Access → Enable TRP Tracker")
            Log.w(TAG, "   Option B (ADB) : adb shell cmd notification allow_listener " +
                    "com.example.mytvxml/com.example.mytvxml.service.TrpNotificationListenerService")
            Log.w(TAG, "   → Trying direct fallback (MEDIA_CONTENT_CONTROL)...")
        } catch (e: Exception) {
            Log.e(TAG, "❌ [NLS] Unexpected error: ${e.message}")
        }

        // ── APPROACH 2: Direct null call (MEDIA_CONTENT_CONTROL) ──
        try {
            val controllers = mediaSessionManager?.getActiveSessions(null)
            if (controllers != null) {
                Log.d(TAG, "✅ [DIRECT] Got ${controllers.size} session(s) via MEDIA_CONTENT_CONTROL")
                return controllers
            }
        } catch (_: SecurityException) {
            Log.w(TAG, "⚠️ [DIRECT] MEDIA_CONTENT_CONTROL not granted either.")
            Log.w(TAG, "   Fix: adb shell pm grant com.example.mytvxml android.permission.MEDIA_CONTENT_CONTROL")
            Log.w(TAG, "   Neither approach worked — no session data this cycle.")
        } catch (e: Exception) {
            Log.e(TAG, "❌ [DIRECT] Unexpected error: ${e.message}")
        }

        return emptyList()
    }

    // ─────────────────────────────────────────────────────────
    // SESSION CHANGE LISTENER
    // Fires the instant a new app starts / stops playing.
    // Also uses the hybrid approach — NLS first, null fallback.
    // ─────────────────────────────────────────────────────────

    private fun setupSessionChangeListener() {
        sessionsChangedListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
            Log.i(TAG, "━━━ Sessions changed — ${controllers?.size ?: 0} active ━━━")
            controllers?.let { handleActiveSessions(it) }
        }

        // Try registering with NLS component first
        var listenerRegistered = false

        try {
            val nlsComponent = ComponentName(this, TrpNotificationListenerService::class.java)
            mediaSessionManager?.addOnActiveSessionsChangedListener(
                sessionsChangedListener!!,
                nlsComponent
            )
            Log.i(TAG, "✅ [NLS] Session change listener registered via NLS")
            listenerRegistered = true
        } catch (_: SecurityException) {
            Log.w(TAG, "⚠️ [NLS] Cannot register listener — NLS not enabled, trying null fallback...")
        } catch (_: Exception) {
            Log.e(TAG, "❌ [NLS] Unexpected error registering listener")
        }

        // Fallback: register with null if NLS failed
        if (!listenerRegistered) {
            try {
                mediaSessionManager?.addOnActiveSessionsChangedListener(
                    sessionsChangedListener!!,
                    null
                )
                Log.i(TAG, "✅ [DIRECT] Session change listener registered via MEDIA_CONTENT_CONTROL")
            } catch (_: SecurityException) {
                Log.w(TAG, "⚠️ [DIRECT] MEDIA_CONTENT_CONTROL not granted — no change listener active.")
                Log.w(TAG, "   Polling will still run every ${POLL_INTERVAL_MS / 1000}s as a fallback.")
            } catch (_: Exception) {
                Log.e(TAG, "❌ [DIRECT] Unexpected error registering null listener")
            }
        }
    }

    // ─────────────────────────────────────────────────────────
    // POLLING — runs every 5s as a safety net
    // ─────────────────────────────────────────────────────────

    private fun startPolling() {
        handler.post(object : Runnable {
            override fun run() {
                scanActiveSessions()
                checkForegroundApp()
                handler.postDelayed(this, POLL_INTERVAL_MS)
            }
        })
    }

    private fun scanActiveSessions() {
        val controllers = getActiveControllers()  // hybrid: NLS → null fallback
        if (controllers.isEmpty()) return
        handleActiveSessions(controllers)
    }

    // ─────────────────────────────────────────────────────────
    // HANDLE SESSIONS — read and log every controller
    // ─────────────────────────────────────────────────────────

    private fun handleActiveSessions(controllers: List<MediaController>) {
        for (controller in controllers) {
            val packageName = controller.packageName ?: "unknown"

            // ── METADATA ────────────────────────────────────
            val metadata     = controller.metadata
            val title        = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)                ?: "Unknown Title"
            val artist       = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)               ?: ""
            val album        = metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM)                ?: ""
            val displayTitle = metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)        ?: ""
            val displaySub   = metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)     ?: ""
            val displayDesc  = metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_DESCRIPTION)  ?: ""
            val genre        = metadata?.getString(MediaMetadata.METADATA_KEY_GENRE)                ?: ""
            val mediaId      = metadata?.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)             ?: ""
            val durationMs   = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION)               ?: -1L

            // ── PLAYBACK STATE ───────────────────────────────
            val state        = controller.playbackState
            val stateStr     = playbackStateToString(state?.state)
            val positionMs   = state?.position ?: 0L
            val speed        = state?.playbackSpeed ?: 0f
            val lastUpdate   = state?.lastPositionUpdateTime ?: 0L
            val extrasInfo   = readExtras(state)

            // ── LOG ───────────────────────────────────��──────
            Log.i(TAG, "┌──────────────────────────────────────────────")
            Log.i(TAG, "│ 📺 MEDIA SESSION DETECTED")
            Log.i(TAG, "│ App Package:       $packageName")
            Log.i(TAG, "│ Title:             $title")
            Log.i(TAG, "│ Display Title:     $displayTitle")
            Log.i(TAG, "│ Display Subtitle:  $displaySub")
            Log.i(TAG, "│ Display Desc:      $displayDesc")
            Log.i(TAG, "│ Artist:            $artist")
            Log.i(TAG, "│ Album:             $album")
            Log.i(TAG, "│ Genre:             $genre")
            Log.i(TAG, "│ Media ID:          $mediaId")
            Log.i(TAG, "│ State:             $stateStr")
            Log.i(TAG, "│ Position:          ${formatDuration(positionMs)} ($positionMs ms)")
            Log.i(TAG, "│ Duration:          ${formatDuration(durationMs)} ($durationMs ms)")
            Log.i(TAG, "│ Playback Speed:    ${speed}x")
            Log.i(TAG, "│ Last Update:       $lastUpdate")
            Log.i(TAG, "│ Extras:            $extrasInfo")
            Log.i(TAG, "│ Timestamp:         ${System.currentTimeMillis()}")
            Log.i(TAG, "└──────────────────────────────────────────────")

            registerCallbackIfNeeded(controller, packageName)
        }
    }

    // ─────────────────────────────────────────────────────────
    // REAL-TIME CALLBACKS
    // Registers a listener so we know the INSTANT something changes
    // ─────────────────────────────────────────────────────────

    private fun registerCallbackIfNeeded(controller: MediaController, packageName: String) {
        if (registeredCallbacks.containsKey(packageName)) return

        val callback = object : MediaController.Callback() {

            override fun onPlaybackStateChanged(state: PlaybackState?) {
                Log.i(TAG, "⚡ PLAYBACK STATE CHANGED [$packageName]")
                Log.i(TAG, "   State:    ${playbackStateToString(state?.state)}")
                Log.i(TAG, "   Position: ${formatDuration(state?.position ?: 0)}")
                Log.i(TAG, "   Speed:    ${state?.playbackSpeed ?: 0f}x")
                Log.i(TAG, "   Time:     ${System.currentTimeMillis()}")
            }

            override fun onMetadataChanged(metadata: MediaMetadata?) {
                Log.i(TAG, "⚡ METADATA CHANGED [$packageName]")
                Log.i(TAG, "   Title:    ${metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "Unknown"}")
                Log.i(TAG, "   Display:  ${metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE) ?: ""}")
                Log.i(TAG, "   Artist:   ${metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: ""}")
                Log.i(TAG, "   Duration: ${formatDuration(metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: -1L)}")
                Log.i(TAG, "   Time:     ${System.currentTimeMillis()}")
            }

            override fun onSessionDestroyed() {
                Log.i(TAG, "⚡ SESSION DESTROYED [$packageName] — app closed or playback ended")
                Log.i(TAG, "   Time: ${System.currentTimeMillis()}")
                registeredCallbacks.remove(packageName)
            }
        }

        try {
            controller.registerCallback(callback, handler)
            registeredCallbacks[packageName] = callback
            Log.i(TAG, "✅ Realtime callback registered for: $packageName")
        } catch (_: Exception) {
            Log.e(TAG, "❌ Failed to register callback for $packageName")
        }
    }

    // ─────────────────────────────────────────────────────────
    // FOREGROUND APP DETECTION
    // Which OTT app is visible on screen right now?
    // Requires PACKAGE_USAGE_STATS:
    //   adb shell appops set com.example.mytvxml PACKAGE_USAGE_STATS allow
    // ─────────────────────────────────────────────────────────

    private fun checkForegroundApp() {
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) return
            val usageStats = getSystemService(USAGE_STATS_SERVICE) as? UsageStatsManager ?: return

            val now = System.currentTimeMillis()
            val usageEvents = usageStats.queryEvents(now - 60_000, now)

            var foregroundPackage: String? = null
            var foregroundActivity: String? = null

            val event = UsageEvents.Event()
            while (usageEvents.hasNextEvent()) {
                usageEvents.getNextEvent(event)
                @Suppress("DEPRECATION")
                if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                    foregroundPackage = event.packageName
                    foregroundActivity = event.className
                }
            }

            if (foregroundPackage != null && foregroundPackage in OTT_PACKAGES) {
                Log.i(TAG, "🖥️ FOREGROUND OTT APP: $foregroundPackage")
                Log.i(TAG, "   Activity: $foregroundActivity")
            }

        } catch (_: SecurityException) {
            Log.w(TAG, "⚠️ Usage Stats not granted — foreground check skipped")
        } catch (_: Exception) {
            Log.e(TAG, "Error checking foreground app")
        }
    }

    // ─────────────────────────────────────────────────────────
    // FOREGROUND NOTIFICATION
    // Required by Android — on TV there's no notification shade so user won't see it.
    // ─────────────────────────────────────────────────────────

    private fun startForegroundNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "TRP Tracker", NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Media analytics tracking"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        startForeground(
            NOTIFICATION_ID,
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("TRP Tracker Active")
                .setContentText("Monitoring media sessions…")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .build()
        )
        Log.i(TAG, "✅ Foreground notification shown")
    }

    // ─────────────────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────────────────

    @Suppress("DEPRECATION")
    private fun readExtras(state: PlaybackState?): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) return "n/a (API<22)"
        val extras = state?.extras ?: return "none"
        return extras.keySet()?.joinToString(", ") { key ->
            "$key=${extras.getString(key) ?: extras.getInt(key)}"
        } ?: "none"
    }

    private fun playbackStateToString(state: Int?): String = when (state) {
        PlaybackState.STATE_NONE                 -> "NONE"
        PlaybackState.STATE_STOPPED              -> "STOPPED"
        PlaybackState.STATE_PAUSED               -> "PAUSED"
        PlaybackState.STATE_PLAYING              -> "PLAYING"
        PlaybackState.STATE_BUFFERING            -> "BUFFERING"
        PlaybackState.STATE_FAST_FORWARDING      -> "FAST_FORWARDING"
        PlaybackState.STATE_REWINDING            -> "REWINDING"
        PlaybackState.STATE_SKIPPING_TO_PREVIOUS -> "SKIP_PREV"
        PlaybackState.STATE_SKIPPING_TO_NEXT     -> "SKIP_NEXT"
        PlaybackState.STATE_ERROR                -> "ERROR"
        else                                     -> "UNKNOWN($state)"
    }

    @SuppressLint("DefaultLocale")
    private fun formatDuration(ms: Long): String {
        if (ms < 0) return "Unknown"
        val s = (ms / 1000) % 60
        val m = (ms / 60_000) % 60
        val h = ms / 3_600_000
        return if (h > 0) String.format("%d:%02d:%02d", h, m, s)
        else String.format("%d:%02d", m, s)
    }
}
