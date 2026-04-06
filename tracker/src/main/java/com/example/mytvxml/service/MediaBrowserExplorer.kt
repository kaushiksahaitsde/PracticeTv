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
 * Discovers which OTT apps expose a MediaBrowserService via PackageManager,
 * then connects to each one using MediaBrowserCompat.
 *
 * Apps that cooperate → we get a MediaSession.Token → full metadata.
 * Apps that reject    → logged as REJECTED, no crash.
 *
 * Logcat filter: TRP_Browser
 */
class MediaBrowserExplorer(private val context: Context) {

    companion object {
        private const val TAG = "TRP_Browser"
        private const val BROWSE_SERVICE_ACTION = "android.media.browse.MediaBrowserService"
    }

    private val handler = Handler(Looper.getMainLooper())
    // Keep STRONG references — GC will disconnect browsers if we don't hold them
    private val activeBrowsers = mutableListOf<MediaBrowserCompat>()

    fun start() {
        Log.i(TAG, "═══════════════════════════════════════════════")
        Log.i(TAG, "  [OPTION 3] MediaBrowserCompat Explorer")
        Log.i(TAG, "  Method: Zero permissions, zero ADB")
        Log.i(TAG, "═══════════════════════════════════════════════")

        val allServices = try {
            val browseIntent = Intent(BROWSE_SERVICE_ACTION)
            context.packageManager.queryIntentServices(browseIntent, PackageManager.GET_META_DATA)
        } catch (e: Exception) {
            Log.e(TAG, "❌ PackageManager query failed: ${e.message}")
            return
        }

        Log.i(TAG, "📦 Total apps with MediaBrowserService on this device: ${allServices.size}")
        allServices.forEach {
            Log.i(TAG, "   Found: ${it.serviceInfo.packageName} / ${it.serviceInfo.name}")
        }

        // ── R&D MODE: OTT filter DISABLED ─────────────────────
        // Connecting to ALL apps to discover which ones accept connections.
        // Re-enable the filter below once R&D is done:
        //
        // val ottServices = allServices.filter {
        //     it.serviceInfo.packageName in MediaTrackerService.OTT_PACKAGES
        // }
        val ottServices = allServices   // ← try everything installed

        if (ottServices.isEmpty()) {
            Log.w(TAG, "⚠️ No app on this device exposes a MediaBrowserService at all.")
            return
        }

        Log.i(TAG, "🔬 R&D MODE — attempting ALL ${ottServices.size} app(s)...")
        ottServices.forEach { connectToBrowser(it.serviceInfo.packageName, it.serviceInfo.name) }
    }

    fun stop() {
        activeBrowsers.forEach { browser ->
            try { if (browser.isConnected) browser.disconnect() } catch (_: Exception) {}
        }
        activeBrowsers.clear()
        handler.removeCallbacksAndMessages(null)
        Log.i(TAG, "MediaBrowserExplorer stopped — all connections closed")
    }

    private fun connectToBrowser(packageName: String, serviceClass: String) {
        Log.i(TAG, "━━━ [OPTION 3] Connecting to: $packageName / $serviceClass")

        lateinit var browser: MediaBrowserCompat
        browser = MediaBrowserCompat(
            context,
            ComponentName(packageName, serviceClass),
            object : MediaBrowserCompat.ConnectionCallback() {

                override fun onConnected() {
                    Log.i(TAG, "┌──────────────────────────────────────────────")
                    Log.i(TAG, "│ ✅ [OPTION 3] BROWSER CONNECTED: $packageName")
                    try {
                        val controller = MediaControllerCompat(context, browser.sessionToken)
                        logControllerData(packageName, controller)
                        controller.registerCallback(object : MediaControllerCompat.Callback() {
                            override fun onPlaybackStateChanged(state: PlaybackStateCompat?) {
                                Log.i(TAG, "⚡ [BROWSER] STATE CHANGED [$packageName]")
                                Log.i(TAG, "   State:    ${compatStateToString(state?.state)}")
                                Log.i(TAG, "   Position: ${formatDuration(state?.position ?: 0L)}")
                                Log.i(TAG, "   Speed:    ${state?.playbackSpeed ?: 0f}x")
                            }
                            override fun onMetadataChanged(metadata: MediaMetadataCompat?) {
                                Log.i(TAG, "⚡ [BROWSER] METADATA CHANGED [$packageName]")
                                Log.i(TAG, "   Title: ${metadata?.getString(MediaMetadataCompat.METADATA_KEY_TITLE) ?: "n/a"}")
                                Log.i(TAG, "   Artist: ${metadata?.getString(MediaMetadataCompat.METADATA_KEY_ARTIST) ?: "n/a"}")
                            }
                            override fun onSessionDestroyed() {
                                Log.i(TAG, "⚡ [BROWSER] SESSION DESTROYED [$packageName]")
                            }
                        }, handler)
                    } catch (e: Exception) {
                        Log.e(TAG, "│ ❌ Error reading from connected browser: ${e.message}")
                        Log.i(TAG, "└──────────────────────────────────────────────")
                    }
                }

                override fun onConnectionFailed() {
                    Log.w(TAG, "❌ [OPTION 3] BROWSER REJECTED by: $packageName")
                    Log.w(TAG, "   App has MediaBrowserService but does NOT allow external connections.")
                }

                override fun onConnectionSuspended() {
                    Log.w(TAG, "⚠️ [OPTION 3] BROWSER SUSPENDED: $packageName")
                }
            },
            null
        )

        activeBrowsers.add(browser)
        try { browser.connect() } catch (e: Exception) {
            Log.e(TAG, "❌ [BROWSER] connect() threw for $packageName: ${e.message}")
        }
    }

    private fun logControllerData(packageName: String, controller: MediaControllerCompat) {
        val metadata     = controller.metadata
        val state        = controller.playbackState
        val title        = metadata?.getString(MediaMetadataCompat.METADATA_KEY_TITLE)               ?: "n/a"
        val displayTitle = metadata?.getString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE)       ?: "n/a"
        val displaySub   = metadata?.getString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE)    ?: "n/a"
        val artist       = metadata?.getString(MediaMetadataCompat.METADATA_KEY_ARTIST)              ?: "n/a"
        val album        = metadata?.getString(MediaMetadataCompat.METADATA_KEY_ALBUM)               ?: "n/a"
        val genre        = metadata?.getString(MediaMetadataCompat.METADATA_KEY_GENRE)               ?: "n/a"
        val mediaId      = metadata?.getString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID)            ?: "n/a"
        val durationMs   = metadata?.getLong(MediaMetadataCompat.METADATA_KEY_DURATION)              ?: -1L
        val positionMs   = state?.position                                                            ?: 0L

        Log.i(TAG, "│ App:            $packageName")
        Log.i(TAG, "│ Title:          $title")
        Log.i(TAG, "│ Display Title:  $displayTitle")
        Log.i(TAG, "│ Display Sub:    $displaySub")
        Log.i(TAG, "│ Artist:         $artist")
        Log.i(TAG, "│ Album:          $album")
        Log.i(TAG, "│ Genre:          $genre")
        Log.i(TAG, "│ Media ID:       $mediaId")
        Log.i(TAG, "│ State:          ${compatStateToString(state?.state)}")
        Log.i(TAG, "│ Position:       ${formatDuration(positionMs)} ($positionMs ms)")
        Log.i(TAG, "│ Duration:       ${formatDuration(durationMs)} ($durationMs ms)")
        Log.i(TAG, "│ Timestamp:      ${System.currentTimeMillis()}")
        Log.i(TAG, "└──────────────────────────────────────────────")
    }

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
        else -> "UNKNOWN($state)"
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
