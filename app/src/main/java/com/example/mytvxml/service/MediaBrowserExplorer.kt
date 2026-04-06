package com.example.mytvxml.service

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log

/**
 * MediaBrowserExplorer — Option 3 (Zero permissions, zero ADB)
 *
 * ══════════════════════════════════════════════════════════
 * HOW IT WORKS
 * ══════════════════════════════════════════════════════════
 * Some OTT apps (Spotify, YouTube etc.) implement Android's MediaBrowserService.
 * This is a public API where an app voluntarily says:
 *   "Anyone can connect to me and ask what I'm playing."
 *
 * MediaBrowserCompat is the client — it knocks on that door.
 * If the app opens the door → we get a MediaSession.Token → full metadata.
 * If the app refuses     → we log "REJECTED" and move on.
 *
 * NO permissions needed. NO ADB. Works on any user's device with the APK.
 *
 * ══════════════════════════════════════════════════════════
 * SMART DISCOVERY (no hardcoded class names)
 * ══════════════════════════════════════════════════════════
 * Instead of guessing each app's internal service class name, we query
 * PackageManager for ALL apps that registered their MediaBrowserService
 * with the standard intent filter ("android.media.browse.MediaBrowserService").
 * This returns exact ComponentName(package, class) for each — zero guessing.
 *
 * ══════════════════════════════════════════════════════════
 * Logcat filter: TRP_Browser
 * ══════════════════════════════════════════════════════════
 */
class MediaBrowserExplorer(private val context: Context) {

    companion object {
        private const val TAG = "TRP_Browser"

        // Standard intent action that all MediaBrowserService apps register
        private const val BROWSE_SERVICE_ACTION = "android.media.browse.MediaBrowserService"
    }

    private val handler = Handler(Looper.getMainLooper())

    // Keep STRONG references — GC will disconnect browsers if we don't hold them
    private val activeBrowsers = mutableListOf<MediaBrowserCompat>()

    // ─────────────────────────────────────────────────────────
    // PUBLIC API
    // ─────────────────────────────────────────────────────────

    /**
     * Discovers OTT apps that expose MediaBrowserService, then tries
     * connecting to each one. Results appear in Logcat under TRP_Browser.
     */
    fun start() {
        Log.i(TAG, "═══════════════════════════════════════════════")
        Log.i(TAG, "  [OPTION 3] MediaBrowserCompat Explorer")
        Log.i(TAG, "  Method: Zero permissions, zero ADB")
        Log.i(TAG, "  How: Query PackageManager → connect to each OTT app")
        Log.i(TAG, "═══════════════════════════════════════════════")

        // Step 1: Find ALL apps on this device that expose a MediaBrowserService
        val allServices = try {
            val browseIntent = Intent(BROWSE_SERVICE_ACTION)
            context.packageManager.queryIntentServices(browseIntent, PackageManager.GET_META_DATA)
        } catch (e: Exception) {
            Log.e(TAG, "❌ PackageManager query failed: ${e.message}")
            return
        }

        Log.i(TAG, "📦 Total apps with MediaBrowserService on this device: ${allServices.size}")
        if (allServices.isNotEmpty()) {
            allServices.forEach {
                Log.i(TAG, "   Found: ${it.serviceInfo.packageName} / ${it.serviceInfo.name}")
            }
        }

        // ── R&D MODE: OTT filter DISABLED ─────────────────────
        // Connecting to ALL apps that expose MediaBrowserService on this device.
        // This lets us discover which apps actually accept connections and share data.
        // Re-enable the filter below once R&D is done.
        //
        // val ottServices = allServices.filter {
        //     it.serviceInfo.packageName in MediaTrackerService.OTT_PACKAGES
        // }
        // if (ottServices.isEmpty()) { ... return }
        val ottServices = allServices   // ← try everything installed

        if (ottServices.isEmpty()) {
            Log.w(TAG, "⚠️ No app on this device exposes a MediaBrowserService at all.")
            return
        }

        Log.i(TAG, "🔬 R&D MODE — attempting ALL ${ottServices.size} app(s) with MediaBrowserService...")

        // Step 3: Connect to each
        ottServices.forEach { resolveInfo ->
            val pkg = resolveInfo.serviceInfo.packageName
            val cls = resolveInfo.serviceInfo.name
            connectToBrowser(pkg, cls)
        }
    }

    /** Disconnects all active browser connections cleanly. */
    fun stop() {
        activeBrowsers.forEach { browser ->
            try {
                if (browser.isConnected) browser.disconnect()
            } catch (_: Exception) {
                // ignore cleanup errors
            }
        }
        activeBrowsers.clear()
        handler.removeCallbacksAndMessages(null)
        Log.i(TAG, "MediaBrowserExplorer stopped — all connections closed")
    }

    // ─────────────────────────────────────────────────────────
    // CONNECT TO A SINGLE APP'S BROWSER SERVICE
    // ─────────────────────────────────────────────────────────

    private fun connectToBrowser(packageName: String, serviceClass: String) {
        Log.i(TAG, "━━━ [OPTION 3] Connecting to: $packageName")
        Log.i(TAG, "    Service: $serviceClass")

        val component = ComponentName(packageName, serviceClass)

        // Use lateinit so the callback can safely reference 'browser' after initialization
        lateinit var browser: MediaBrowserCompat

        browser = MediaBrowserCompat(
            context,
            component,
            object : MediaBrowserCompat.ConnectionCallback() {

                /**
                 * App accepted our connection request.
                 * We can now read its MediaSession token and get full player data.
                 */
                override fun onConnected() {
                    Log.i(TAG, "┌──────────────────────────────────────────────")
                    Log.i(TAG, "│ ✅ [OPTION 3] BROWSER CONNECTED: $packageName")
                    Log.i(TAG, "│ App opened its MediaBrowserService door to us!")

                    try {
                        val sessionToken = browser.sessionToken
                        val controller   = MediaControllerCompat(context, sessionToken)

                        logControllerData(packageName, controller)

                        // Register for real-time updates
                        controller.registerCallback(
                            object : MediaControllerCompat.Callback() {

                                override fun onPlaybackStateChanged(state: PlaybackStateCompat?) {
                                    Log.i(TAG, "⚡ [BROWSER] STATE CHANGED [$packageName]")
                                    Log.i(TAG, "   State:    ${compatStateToString(state?.state)}")
                                    Log.i(TAG, "   Position: ${formatDuration(state?.position ?: 0L)}")
                                    Log.i(TAG, "   Speed:    ${state?.playbackSpeed ?: 0f}x")
                                    Log.i(TAG, "   Time:     ${System.currentTimeMillis()}")
                                }

                                override fun onMetadataChanged(metadata: MediaMetadataCompat?) {
                                    Log.i(TAG, "⚡ [BROWSER] METADATA CHANGED [$packageName]")
                                    Log.i(TAG, "   Title:    ${metadata?.getString(MediaMetadataCompat.METADATA_KEY_TITLE) ?: "n/a"}")
                                    Log.i(TAG, "   Display:  ${metadata?.getString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE) ?: "n/a"}")
                                    Log.i(TAG, "   Artist:   ${metadata?.getString(MediaMetadataCompat.METADATA_KEY_ARTIST) ?: "n/a"}")
                                    Log.i(TAG, "   Duration: ${formatDuration(metadata?.getLong(MediaMetadataCompat.METADATA_KEY_DURATION) ?: -1L)}")
                                    Log.i(TAG, "   Time:     ${System.currentTimeMillis()}")
                                }

                                override fun onSessionDestroyed() {
                                    Log.i(TAG, "⚡ [BROWSER] SESSION DESTROYED [$packageName]")
                                    Log.i(TAG, "   The app closed its MediaSession (app closed or playback ended)")
                                }
                            },
                            handler
                        )

                    } catch (e: Exception) {
                        Log.e(TAG, "│ ❌ Error reading from connected browser: ${e.message}")
                        Log.i(TAG, "└──────────────────────────────────────────────")
                    }
                }

                /**
                 * App rejected our connection attempt.
                 * This is normal for apps like Netflix that don't allow external browsers.
                 */
                override fun onConnectionFailed() {
                    Log.w(TAG, "❌ [OPTION 3] BROWSER REJECTED by: $packageName")
                    Log.w(TAG, "   The app has MediaBrowserService but does NOT allow external connections.")
                    Log.w(TAG, "   This is a deliberate security choice by that app.")
                }

                override fun onConnectionSuspended() {
                    Log.w(TAG, "⚠️ [OPTION 3] BROWSER SUSPENDED: $packageName")
                    Log.w(TAG, "   Connection was lost (app minimized or killed)")
                }
            },
            null  // optional hints bundle — null is fine
        )

        // Keep a strong reference before connecting
        activeBrowsers.add(browser)

        try {
            browser.connect()
        } catch (e: Exception) {
            Log.e(TAG, "❌ [BROWSER] connect() threw for $packageName: ${e.message}")
        }
    }

    // ─────────────────────────────────────────────────────────
    // LOGGING HELPER
    // ─────────────────────────────────────────────────────────

    private fun logControllerData(packageName: String, controller: MediaControllerCompat) {
        val metadata = controller.metadata
        val state    = controller.playbackState

        val title        = metadata?.getString(MediaMetadataCompat.METADATA_KEY_TITLE)               ?: "n/a"
        val displayTitle = metadata?.getString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE)       ?: "n/a"
        val displaySub   = metadata?.getString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE)    ?: "n/a"
        val artist       = metadata?.getString(MediaMetadataCompat.METADATA_KEY_ARTIST)              ?: "n/a"
        val album        = metadata?.getString(MediaMetadataCompat.METADATA_KEY_ALBUM)               ?: "n/a"
        val genre        = metadata?.getString(MediaMetadataCompat.METADATA_KEY_GENRE)               ?: "n/a"
        val mediaId      = metadata?.getString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID)            ?: "n/a"
        val durationMs   = metadata?.getLong(MediaMetadataCompat.METADATA_KEY_DURATION)              ?: -1L
        val positionMs   = state?.position                                                            ?: 0L
        val stateStr     = compatStateToString(state?.state)
        val speed        = state?.playbackSpeed                                                       ?: 0f

        Log.i(TAG, "│ App:            $packageName")
        Log.i(TAG, "│ Title:          $title")
        Log.i(TAG, "│ Display Title:  $displayTitle")
        Log.i(TAG, "│ Display Sub:    $displaySub")
        Log.i(TAG, "│ Artist:         $artist")
        Log.i(TAG, "│ Album:          $album")
        Log.i(TAG, "│ Genre:          $genre")
        Log.i(TAG, "│ Media ID:       $mediaId")
        Log.i(TAG, "│ State:          $stateStr")
        Log.i(TAG, "│ Position:       ${formatDuration(positionMs)} ($positionMs ms)")
        Log.i(TAG, "│ Duration:       ${formatDuration(durationMs)} ($durationMs ms)")
        Log.i(TAG, "│ Speed:          ${speed}x")
        Log.i(TAG, "│ Timestamp:      ${System.currentTimeMillis()}")
        Log.i(TAG, "└──────────────────────────────────────────────")
    }

    // ─────────────────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────────────────

    private fun compatStateToString(state: Int?): String = when (state) {
        PlaybackStateCompat.STATE_NONE        -> "NONE"
        PlaybackStateCompat.STATE_STOPPED     -> "STOPPED"
        PlaybackStateCompat.STATE_PAUSED      -> "PAUSED"
        PlaybackStateCompat.STATE_PLAYING     -> "PLAYING"
        PlaybackStateCompat.STATE_BUFFERING   -> "BUFFERING"
        PlaybackStateCompat.STATE_ERROR       -> "ERROR"
        PlaybackStateCompat.STATE_FAST_FORWARDING -> "FAST_FWD"
        PlaybackStateCompat.STATE_REWINDING   -> "REWINDING"
        PlaybackStateCompat.STATE_SKIPPING_TO_PREVIOUS -> "SKIP_PREV"
        PlaybackStateCompat.STATE_SKIPPING_TO_NEXT     -> "SKIP_NEXT"
        else                                  -> "UNKNOWN($state)"
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
