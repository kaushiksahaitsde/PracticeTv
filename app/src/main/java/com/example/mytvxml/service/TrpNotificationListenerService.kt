package com.example.mytvxml.service

import android.annotation.SuppressLint
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

/**
 * TrpNotificationListenerService
 *
 * This service serves TWO purposes:
 *
 * ══════════════════════════════════════════════════════════
 * PURPOSE 1 — MediaSession access gate (for Option 1 in MediaTrackerService)
 * ══════════════════════════════════════════════════════════
 * When this service is enabled by the user (Settings → Notification Access),
 * MediaTrackerService can call getActiveSessions(this component) to get
 * full MediaController access to all apps. No ADB needed.
 *
 * ══════════════════════════════════════════════════════════
 * PURPOSE 2 — Option 2: Extract MediaSession.Token from notification extras
 * ══════════════════════════════════════════════════════════
 * Logcat tag: TRP_NotifToken
 *
 * When an OTT app posts a media-style notification (play/pause card on screen),
 * Android embeds the app's MediaSession.Token directly inside the notification extras.
 * We extract that token, build a MediaController from it, and read the FULL
 * player metadata (title, artist, duration, position, state, etc.) directly —
 * exactly as if we had called getActiveSessions().
 *
 * ZERO extra permissions needed beyond what NLS already grants.
 * This is completely invisible to the user — no extra Settings screen to open.
 *
 * How to enable:
 *   • User: Settings → Special App Access → Notification Access → Enable TRP Tracker
 *   • ADB : adb shell cmd notification allow_listener \
 *             com.example.mytvxml/com.example.mytvxml.service.TrpNotificationListenerService
 */
class TrpNotificationListenerService : NotificationListenerService() {

    companion object {
        private const val TAG_NOTIF  = "TRP_NotifListener"   // Purpose 1 logs
        private const val TAG_TOKEN  = "TRP_NotifToken"      // Option 2 logs

        // Extra key Android uses to embed the MediaSession.Token in media notifications
        private const val EXTRA_SESSION_TOKEN = "android.media.session.MediaSession.Token"

        val OTT_PACKAGES = setOf(
            "in.startv.hotstar",               // Disney+ Hotstar
            "com.hotstar.android",              // Hotstar (alternate)
            "com.google.android.youtube",       // YouTube
            "com.google.android.youtube.tv",    // YouTube TV
            "com.google.android.youtube.tvkids",// YouTube Kids TV
            "com.amazon.avod",                  // Amazon Prime Video
            "com.amazon.firetv.youtube",        // YouTube on Fire TV
            "com.netflix.ninja",                // Netflix (TV)
            "com.netflix.mediaclient",          // Netflix
            "com.sonyliv",                      // SonyLIV
            "com.viacom18.vootkids",            // Voot Kids
            "com.jio.media.jiobeats",           // JioCinema
            "com.jio.jioplay.tv",               // JioTV
            "com.tv.v18.viola",                 // JioCinema (alternate)
            "com.graymatrix.did",               // ZEE5
            "com.erosnow",                      // Eros Now
            "com.mxplayer.android",             // MX Player
            "com.spotify.tv.android",           // Spotify TV
            "com.apple.atve.androidtv.appletv", // Apple TV
        )
    }

    // ─────────────────────────────────────────────────────────
    // LISTENER LIFECYCLE
    // ─────────────────────────────────────────────────────────

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i(TAG_NOTIF, "✅ NotificationListener connected")
        Log.i(TAG_NOTIF, "   Option 1 (MediaTrackerService) can now call getActiveSessions()")
        Log.i(TAG_NOTIF, "   Option 2 (token extraction) active — watching OTT notifications")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.w(TAG_NOTIF, "⚠️ NotificationListener disconnected — MediaSession access lost!")
    }

    // ─────────────────────────────────────────────────────────
    // NOTIFICATION POSTED
    // ─────────────────────────────────────────────────────────

    override fun onNotificationPosted(sbn: StatusBarNotification?, rankingMap: RankingMap?) {
        sbn ?: return
        val packageName = sbn.packageName
        if (packageName !in OTT_PACKAGES) return

        val extras = sbn.notification.extras

        // ── PURPOSE 1 LOG: Basic notification text ───────────
        val notifTitle   = extras.getCharSequence("android.title")?.toString()   ?: "Unknown"
        val notifText    = extras.getCharSequence("android.text")?.toString()    ?: ""
        val notifSubText = extras.getCharSequence("android.subText")?.toString() ?: ""

        Log.i(TAG_NOTIF, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        Log.i(TAG_NOTIF, "📺 OTT NOTIFICATION DETECTED  [Option 1 side-data]")
        Log.i(TAG_NOTIF, "   App:     $packageName")
        Log.i(TAG_NOTIF, "   Title:   $notifTitle")
        Log.i(TAG_NOTIF, "   Text:    $notifText")
        Log.i(TAG_NOTIF, "   SubText: $notifSubText")
        Log.i(TAG_NOTIF, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

        // ── OPTION 2: Extract hidden MediaSession.Token ───────
        tryExtractSessionToken(packageName, sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        sbn ?: return
        if (sbn.packageName in OTT_PACKAGES) {
            Log.i(TAG_NOTIF, "📺 OTT notification removed: ${sbn.packageName}")
        }
    }

    // ─────────────────────────────────────────────────────────
    // OPTION 2: TOKEN EXTRACTION
    //
    // Every media-style notification contains a hidden MediaSession.Token
    // in its extras under key "android.media.session.MediaSession.Token".
    // With that token we can build a full MediaController and read everything.
    // ─────────────────────────────────────────────────────────

    @Suppress("DEPRECATION")
    private fun tryExtractSessionToken(packageName: String, sbn: StatusBarNotification) {
        try {
            val extras = sbn.notification.extras

            // Extract the embedded MediaSession.Token from the notification bundle
            val token: MediaSession.Token? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                extras.getParcelable(EXTRA_SESSION_TOKEN, MediaSession.Token::class.java)
            } else {
                extras.getParcelable(EXTRA_SESSION_TOKEN)
            }

            if (token == null) {
                Log.d(TAG_TOKEN, "ℹ️  [$packageName] No MediaSession.Token in notification extras")
                Log.d(TAG_TOKEN, "   (App may not use MediaStyle notification — notification data above is all we get)")
                return
            }

            // We have the token! Build a MediaController directly — no getActiveSessions() needed.
            val controller = MediaController(applicationContext, token)

            val metadata   = controller.metadata
            val state      = controller.playbackState

            val title        = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)                ?: "n/a"
            val displayTitle = metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)        ?: "n/a"
            val displaySub   = metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)     ?: "n/a"
            val artist       = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)               ?: "n/a"
            val album        = metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM)                ?: "n/a"
            val genre        = metadata?.getString(MediaMetadata.METADATA_KEY_GENRE)                ?: "n/a"
            val mediaId      = metadata?.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)             ?: "n/a"
            val durationMs   = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION)               ?: -1L
            val positionMs   = state?.position                                                       ?: 0L
            val stateStr     = playbackStateToString(state?.state)
            val speed        = state?.playbackSpeed                                                  ?: 0f

            Log.i(TAG_TOKEN, "┌──────────────────────────────────────────────")
            Log.i(TAG_TOKEN, "│ 🎯 [OPTION 2] TOKEN EXTRACTED FROM NOTIFICATION")
            Log.i(TAG_TOKEN, "│ App:            $packageName")
            Log.i(TAG_TOKEN, "│ Title:          $title")
            Log.i(TAG_TOKEN, "│ Display Title:  $displayTitle")
            Log.i(TAG_TOKEN, "│ Display Sub:    $displaySub")
            Log.i(TAG_TOKEN, "│ Artist:         $artist")
            Log.i(TAG_TOKEN, "│ Album:          $album")
            Log.i(TAG_TOKEN, "│ Genre:          $genre")
            Log.i(TAG_TOKEN, "│ Media ID:       $mediaId")
            Log.i(TAG_TOKEN, "│ State:          $stateStr")
            Log.i(TAG_TOKEN, "│ Position:       ${formatDuration(positionMs)} ($positionMs ms)")
            Log.i(TAG_TOKEN, "│ Duration:       ${formatDuration(durationMs)} ($durationMs ms)")
            Log.i(TAG_TOKEN, "│ Speed:          ${speed}x")
            Log.i(TAG_TOKEN, "│ Timestamp:      ${System.currentTimeMillis()}")
            Log.i(TAG_TOKEN, "└──────────────────────────────────────────────")

        } catch (e: Exception) {
            Log.e(TAG_TOKEN, "❌ [$packageName] Error extracting session token: ${e.message}")
        }
    }

    // ─────────────────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────────────────

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
