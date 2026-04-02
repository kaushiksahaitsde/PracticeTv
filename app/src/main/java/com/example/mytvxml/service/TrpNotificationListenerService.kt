package com.example.mytvxml.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class TrpNotificationListenerService: NotificationListenerService() {

    companion object{
        private const val TAG = "TRP_NotifListener"
        val OTT_PACKAGES = setOf(
            "in.startv.hotstar",              // Disney+ Hotstar
            "com.hotstar.android",             // Hotstar (alternate package)
            "com.google.android.youtube",       // YouTube
            "com.google.android.youtube.tv",    // YouTube for TV
            "com.google.android.youtube.tvkids", // YouTube Kids TV
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

    override fun onNotificationPosted(sbn: StatusBarNotification?, rankingMap: RankingMap?) {
       sbn?:return
        val packageName = sbn.packageName
        if(packageName in OTT_PACKAGES){
            val extras=sbn.notification.extras
            val title = extras.getCharSequence("android.title")?.toString() ?: "Unknown"
            val text = extras.getCharSequence("android.text")?.toString() ?: ""
            val subText = extras.getCharSequence("android.subText")?.toString() ?: ""

            Log.i(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            Log.i(TAG, "📺 OTT NOTIFICATION DETECTED")
            Log.i(TAG, "   App: $packageName")
            Log.i(TAG, "   Title: $title")
            Log.i(TAG, "   Text: $text")
            Log.i(TAG, "   SubText: $subText")
            Log.i(TAG, "   Time: ${System.currentTimeMillis()}")
            Log.i(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        sbn ?: return

        if (sbn.packageName in OTT_PACKAGES) {
            Log.i(TAG, "📺 OTT notification removed: ${sbn.packageName}")
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i(TAG, "✅ NotificationListener connected — we can now read MediaSessions!")
    }


    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.w(TAG, "⚠️ NotificationListener disconnected — we lost MediaSession access!")

    }

    }




