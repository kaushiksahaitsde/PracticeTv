Got it — I’ll first inspect your existing project and `ClaudeResearch.md`, then I’ll give you a **production-style, copy-paste-ready implementation plan** with TV-friendly UI, service flow, and memory-safe tracking architecture.  
One constraint: I’m in **read-only mode** right now, so I’ll provide exact code snippets for you to paste, but I can’t apply edits directly yet.

I reviewed your current code and `ClaudeResearch.md`; next I’ll quickly inspect your `AndroidManifest` and layout files so I can give you **exact, project-matching copy-paste code** with a TV-friendly start button and tracker flow.

## What’s realistically possible (important)

You **cannot capture “all internal player data”** from Hotstar/YouTube.  
You can only capture what those apps expose through Android media APIs (mainly `MediaSession` metadata + playback state).

- **Best official approach**: `NotificationListenerService` + `MediaSessionManager` + `MediaController`
- **Not for this use case**: Media3/ExoPlayer is for **your own app’s playback**, not reading other apps’ private internals.

References: [Media3 overview](https://developer.android.com/media/media3), [Android TV memory/playback guidance](https://developer.android.com/training/tv/playback/memory)

---

## Read-only mode note

I’m currently in **read-only mode**, so I can’t edit your project directly.  
Below is **copy-paste ready code** matched to your package: `com.example.mytvxml`.

---

## 1) `AndroidManifest.xml` (replace/add relevant parts)

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <uses-permission
        android:name="android.permission.PACKAGE_USAGE_STATS"
        tools:ignore="ProtectedPermissions" />

    <uses-feature android:name="android.hardware.touchscreen" android:required="false" />
    <uses-feature android:name="android.software.leanback" android:required="true" />

    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:theme="@style/Theme.MyTvXml">

        <activity
            android:name=".MainActivity"
            android:banner="@drawable/app_icon_your_company"
            android:exported="true"
            android:icon="@drawable/app_icon_your_company"
            android:label="@string/app_name"
            android:logo="@drawable/app_icon_your_company"
            android:screenOrientation="landscape">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LEANBACK_LAUNCHER" />
            </intent-filter>
        </activity>

        <service
            android:name=".service.MediaTrackerService"
            android:exported="false"
            android:foregroundServiceType="specialUse">
            <property
                android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
                android:value="TRP media analytics tracking" />
        </service>

        <service
            android:name=".service.TrpNotificationListenerService"
            android:exported="true"
            android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE">
            <intent-filter>
                <action android:name="android.service.notification.NotificationListenerService" />
            </intent-filter>
        </service>
    </application>
</manifest>
```

---

## 2) Add TV-friendly start button UI (`activity_main.xml`)

Add these views near your title/description section:

```xml
<Button
    android:id="@+id/btn_start_tracking"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:text="Start TRP Tracking"
    android:textAllCaps="false"
    android:textStyle="bold"
    android:paddingHorizontal="28dp"
    android:paddingVertical="14dp"
    android:background="@drawable/tv_btn_primary"
    android:textColor="@android:color/white"
    android:focusable="true"
    android:focusableInTouchMode="true"
    app:layout_constraintTop_toBottomOf="@id/description"
    app:layout_constraintStart_toStartOf="@id/guideline_start"
    android:layout_marginTop="20dp" />

<TextView
    android:id="@+id/txt_tracking_status"
    android:layout_width="0dp"
    android:layout_height="wrap_content"
    android:text="Tracking: OFF"
    android:textColor="#B3FFFFFF"
    android:textSize="14sp"
    app:layout_constraintTop_toBottomOf="@id/btn_start_tracking"
    app:layout_constraintStart_toStartOf="@id/guideline_start"
    app:layout_constraintWidth_percent=".45"
    android:layout_marginTop="12dp" />
```

Create `res/drawable/tv_btn_primary.xml`:

```xml
<selector xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:state_focused="true">
        <shape android:shape="rectangle">
            <corners android:radius="16dp" />
            <solid android:color="#2979FF" />
            <stroke android:width="2dp" android:color="#FFFFFF" />
        </shape>
    </item>
    <item>
        <shape android:shape="rectangle">
            <corners android:radius="16dp" />
            <solid android:color="#1565C0" />
        </shape>
    </item>
</selector>
```

---

## 3) `MainActivity.kt` additions (start service + permissions entry points)

Add fields and logic:

```kotlin
private lateinit var btnStartTracking: Button
private lateinit var txtTrackingStatus: TextView
```

In `onCreate` after your existing `findViewById`:

```kotlin
btnStartTracking = findViewById(R.id.btn_start_tracking)
txtTrackingStatus = findViewById(R.id.txt_tracking_status)

btnStartTracking.setOnClickListener {
    if (!isNotificationListenerEnabled()) {
        startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        return@setOnClickListener
    }
    if (!isUsageAccessGranted()) {
        startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        return@setOnClickListener
    }

    val serviceIntent = Intent(this, com.example.mytvxml.service.MediaTrackerService::class.java)
    ContextCompat.startForegroundService(this, serviceIntent)
    txtTrackingStatus.text = "Tracking: ON"
}
```

Add helpers:

```kotlin
private fun isNotificationListenerEnabled(): Boolean {
    val enabled = Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: return false
    return enabled.contains("$packageName/com.example.mytvxml.service.TrpNotificationListenerService")
}

private fun isUsageAccessGranted(): Boolean {
    val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName)
    return mode == AppOpsManager.MODE_ALLOWED
}
```

---

## 4) Create `service/TrpNotificationListenerService.kt`

```kotlin
package com.example.mytvxml.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class TrpNotificationListenerService : NotificationListenerService() {
    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i("TRP_Notif", "Notification listener connected")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        Log.d("TRP_Notif", "Notification from: ${sbn.packageName}")
    }
}
```

---

## 5) Create `service/MediaTrackerService.kt` (core tracker)

```kotlin
package com.example.mytvxml.service

import android.app.*
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat

class MediaTrackerService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var mediaSessionManager: MediaSessionManager? = null
    private val callbacks = mutableMapOf<MediaSession.Token, Pair<MediaController, MediaController.Callback>>()

    private val scanRunnable = object : Runnable {
        override fun run() {
            scanSessions()
            handler.postDelayed(this, 5000L) // low overhead poll
        }
    }

    override fun onCreate() {
        super.onCreate()
        startAsForeground()
        mediaSessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        handler.post(scanRunnable)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        callbacks.values.forEach { (controller, callback) -> controller.unregisterCallback(callback) }
        callbacks.clear()
    }

    private fun startAsForeground() {
        val channelId = "trp_tracker_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "TRP Tracker", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("TRP Tracker Running")
            .setContentText("Tracking media sessions")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .build()
        startForeground(1201, notification)
    }

    private fun scanSessions() {
        try {
            val component = ComponentName(this, TrpNotificationListenerService::class.java)
            val controllers = mediaSessionManager?.getActiveSessions(component).orEmpty()
            val activeTokens = controllers.map { it.sessionToken }.toSet()

            // unregister dead sessions
            val dead = callbacks.keys.filter { it !in activeTokens }
            dead.forEach { token ->
                callbacks[token]?.let { (controller, callback) -> controller.unregisterCallback(callback) }
                callbacks.remove(token)
            }

            // register + log current sessions
            controllers.forEach { controller ->
                logController(controller)
                if (!callbacks.containsKey(controller.sessionToken)) {
                    val cb = object : MediaController.Callback() {
                        override fun onPlaybackStateChanged(state: PlaybackState?) = logController(controller)
                        override fun onMetadataChanged(metadata: MediaMetadata?) = logController(controller)
                    }
                    controller.registerCallback(cb, handler)
                    callbacks[controller.sessionToken] = controller to cb
                }
            }
        } catch (_: SecurityException) {
            // permission not enabled yet
        }
    }

    private fun logController(controller: MediaController) {
        val md = controller.metadata
        val st = controller.playbackState

        val title = md?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty()
        val artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty()
        val duration = md?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: -1L
        val position = st?.position ?: 0L
        val state = when (st?.state) {
            PlaybackState.STATE_PLAYING -> "PLAYING"
            PlaybackState.STATE_PAUSED -> "PAUSED"
            PlaybackState.STATE_BUFFERING -> "BUFFERING"
            PlaybackState.STATE_STOPPED -> "STOPPED"
            else -> "OTHER"
        }

        android.util.Log.i(
            "TRP_Tracker",
            "pkg=${controller.packageName}, title=$title, artist=$artist, state=$state, pos=$position, dur=$duration"
        )
    }
}
```

---

## 6) TV memory + performance rules you should follow

- **Keep tracker lightweight**: polling every 5–7s is enough for R&D.
- **Never decode/store artwork bitmaps** from other sessions.
- **Do not keep huge in-memory logs**; use Logcat first, file batching later.
- **Unregister callbacks** when sessions disappear (done above).
- On low-RAM TVs, target very low footprint (official guidance emphasizes strict memory discipline on TV devices).  
  Reference: [Android TV memory optimization](https://developer.android.com/training/tv/playback/memory)

---

## 7) Final reality check

- **Yes**, this architecture is the right starting point for your TRP demo.
- **No**, you won’t get private/internal decoder/player internals from OTT apps.
- You’ll get: app package, title/artist (if provided), play/pause/buffering, position, duration, session lifecycle events.

If you switch me to **write mode**, I can apply all of this directly into your project and resolve compile issues end-to-end.