# TRP/Analytics Tracker for Android TV — Complete R&D Guide

## Table of Contents
1. [The Big Picture — What Are We Building?](#1-the-big-picture)
2. [How Does This Actually Work? (The Core Concept)](#2-core-concept)
3. [Android TV Constraints & What To Watch Out For](#3-tv-constraints)
4. [Architecture Overview](#4-architecture)
5. [Step-by-Step Implementation](#5-implementation)
6. [Complete Code — Copy-Paste Ready](#6-complete-code)
7. [What Data Can We Actually Capture?](#7-data-we-can-capture)
8. [Optimization Tips for TV](#8-optimization)
9. [Testing & Debugging](#9-testing)
10. [Limitations & Honest Reality Check](#10-limitations)

---

## 1. The Big Picture — What Are We Building? {#1-the-big-picture}

Imagine you have a **spy camera in a movie theater** (legally, with consent!). You're not recording the movie — you're just noting down:
- "The movie started at 7:00 PM"
- "It's a Bollywood film called XYZ"
- "The viewer paused at 7:45 PM"
- "They resumed at 7:50 PM"
- "Movie ended at 9:30 PM"

That's exactly what our TRP app does, but on an Android TV. When someone watches Hotstar, YouTube, or any OTT app, our app sits quietly in the background and collects **what they're watching, when they're watching, and playback events** (play, pause, skip, etc.).

### The Key Players in Our System

Think of it like this:

```
┌─────────────────────────────────────────────────────┐
│                    ANDROID TV                        │
│                                                      │
│  ┌──────────────┐     ┌──────────────────────────┐  │
│  │   HOTSTAR     │     │   OUR TRP APP            │  │
│  │   (or YouTube │     │   (Foreground Service)    │  │
│  │    or any     │     │                          │  │
│  │    OTT app)   │     │   Watches silently...    │  │
│  │              │     │   Logs everything...     │  │
│  │  User is     │◄────┤   Doesn't disturb user   │  │
│  │  watching    │     │                          │  │
│  │  content     │     │   Captures:              │  │
│  │  here        │     │   - Song/Video title     │  │
│  │              │     │   - Play/Pause events    │  │
│  │              │     │   - Which app is playing  │  │
│  │              │     │   - Duration watched      │  │
│  └──────────────┘     └──────────────────────────┘  │
│                                                      │
└─────────────────────────────────────────────────────┘
```

---

## 2. How Does This Actually Work? (The Core Concept) {#2-core-concept}

### The Magic Key: MediaSession

When apps like Hotstar or YouTube play a video, Android **requires** them to create something called a **MediaSession**. Think of MediaSession as a **public bulletin board** that the app puts up saying:

> "Hey everyone! I'm playing 'Pushpa 2 — The Rule'. It's currently at 00:45:30. It's playing, not paused."

This bulletin board is publicly readable by any app that has the right permission. **Our TRP app reads this bulletin board.**

### How Do We Read Other Apps' Bulletin Boards?

Android gives us two tools:

**Tool 1: `MediaSessionManager`** — This is like having the **master key** to see ALL bulletin boards (MediaSessions) that are currently active on the TV. It tells us: "Right now, Hotstar has one bulletin board, YouTube has another."

**Tool 2: `MediaController`** — Once we find a bulletin board, this is the **magnifying glass** that lets us read every detail on it. It tells us the song/video title, artist, album art, current position, duration, play/pause state, etc.

**Tool 3: `NotificationListenerService`** — This is the **permission ticket** we need. Android says "You can only read other apps' MediaSessions if you have Notification Listener permission." Since your users are paid and will accept all permissions, this is fine.

### The Permission Flow (Simple Version)

```
User installs TRP app
        │
        ▼
App asks: "Please enable Notification Listener"
        │
        ▼
User goes to Settings → enables it
        │
        ▼
NOW our app can see ALL MediaSessions from ALL apps
        │
        ▼
We start our Foreground Service
        │
        ▼
Service watches for any app playing media
        │
        ▼
When detected → we log everything
```

---

## 3. Android TV Constraints & What To Watch Out For {#3-tv-constraints}

This is **critical**. TV is NOT a phone. Here's what's different:

### Memory is Limited
TVs typically have **1 GB to 2 GB of RAM** compared to phones having 6-12 GB. Our app must be extremely lightweight. If our app eats too much memory, Android will **kill the app the user is actually watching** (Hotstar/YouTube) to free memory. That's the worst thing that could happen — we destroy the viewing experience.

**Our Target:** Our foreground service should use **less than 30 MB of RAM.**

### Foreground Service Rules on TV
Google's official rule for Android TV states:
- Audio apps CAN keep a foreground service running after the user leaves the app (to continue playing music).
- **For other types of apps, foreground services should stop when the user leaves the app.**

BUT — here's the important thing — this is Google's Play Store guideline. Since your app is a **custom/sideloaded TRP application** for paid users (not going to Play Store), you have more flexibility. The foreground service will still work technically; it's just not Play Store compliant.

### No Notification Tray
Phones show the foreground service notification in the pull-down shade. **TVs don't have that.** The notification exists (Android requires it), but the user won't see it on screen. This is actually perfect for us — our service runs without visually disturbing the user.

### The Low Memory Killer (LMK)
Android TV has an aggressive process called **Low Memory Killer**. When the TV runs out of RAM, it starts killing apps in this order:
1. Empty/cached background apps (killed first)
2. Background services
3. **Foreground services** (killed only in extreme cases)
4. **Visible/active app** (killed last)

Since we're a **foreground service**, we're relatively safe. But we must keep our memory usage tiny.

---

## 4. Architecture Overview {#4-architecture}

Our app has these components:

```
┌────────────────────────────────────────────────────────────────┐
│                     TRP TRACKER APP                             │
│                                                                 │
│  ┌──────────────────┐   ┌───────────────────────────────────┐  │
│  │  MainActivity     │   │  TrpNotificationListener          │  │
│  │  (Launch screen)  │   │  (extends NotificationListener    │  │
│  │                   │   │   Service)                         │  │
│  │  - Start button   │   │                                   │  │
│  │  - Stop button    │   │  - Gives us permission to read    │  │
│  │  - Status display │   │    other apps' MediaSessions      │  │
│  └────────┬─────────┘   └───────────────────────────────────┘  │
│           │                                                     │
│           │ starts                                              │
│           ▼                                                     │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │  MediaTrackerService (Foreground Service)                 │  │
│  │                                                           │  │
│  │  ┌─────────────────────┐  ┌───────────────────────────┐  │  │
│  │  │ MediaSessionWatcher  │  │ ForegroundAppTracker       │  │  │
│  │  │                     │  │                           │  │  │
│  │  │ Uses:               │  │ Uses:                     │  │  │
│  │  │ - MediaSessionMgr   │  │ - UsageStatsManager       │  │  │
│  │  │ - MediaController   │  │                           │  │  │
│  │  │ - Callbacks         │  │ Tells us WHICH app is     │  │  │
│  │  │                     │  │ currently in foreground    │  │  │
│  │  │ Gives us:           │  │ (Hotstar? YouTube? etc.)  │  │  │
│  │  │ - Title, Artist     │  │                           │  │  │
│  │  │ - Play/Pause state  │  └───────────────────────────┘  │  │
│  │  │ - Position/Duration │                                  │  │
│  │  │ - Playback speed    │  ┌───────────────────────────┐  │  │
│  │  └─────────────────────┘  │ TrpLogger                 │  │  │
│  │                           │                           │  │  │
│  │                           │ Writes all captured data  │  │  │
│  │                           │ to log files on device    │  │  │
│  │                           │ (Later: upload to server) │  │  │
│  │                           └───────────────────────────┘  │  │
│  └──────────────────────────────────────────────────────────┘  │
└────────────────────────────────────────────────────────────────┘
```

---

## 5. Step-by-Step Implementation {#5-implementation}

### Step 1: Project Setup

Create a new Android TV project in Android Studio with these settings:
- **Minimum SDK:** API 21 (Android 5.0 Lollipop)
- **Target SDK:** API 34 (Android 14)
- **Language:** Kotlin

### Step 2: Add Dependencies

In your `app/build.gradle`:

```groovy
plugins {
    id 'com.android.application'
    id 'org.jetbrains.kotlin.android'
}

android {
    namespace 'com.yourcompany.trptracker'
    compileSdk 34

    defaultConfig {
        applicationId "com.yourcompany.trptracker"
        minSdk 21
        targetSdk 34
        versionCode 1
        versionName "1.0"
    }

    buildTypes {
        release {
            minifyEnabled false
        }
    }
    
    compileOptions {
        sourceCompatibility JavaVersion.VERSION_17
        targetCompatibility JavaVersion.VERSION_17
    }
    
    kotlinOptions {
        jvmTarget = '17'
    }
}

dependencies {
    implementation 'androidx.core:core-ktx:1.12.0'
    implementation 'androidx.leanback:leanback:1.0.0'
    implementation 'androidx.appcompat:appcompat:1.6.1'
    
    // Coroutines - for doing things without blocking the main thread
    implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3'
}
```

**What each dependency does (in simple words):**
- `core-ktx` — Kotlin extensions that make Android code cleaner
- `leanback` — Android TV's UI library (the TV-style interface)
- `appcompat` — Backward compatibility helpers
- `kotlinx-coroutines` — Lets us run code in the background without freezing the UI

### Step 3: AndroidManifest.xml

This is the **most important file.** It tells Android what permissions our app needs and what components it has.

---

## 6. Complete Code — Copy-Paste Ready {#6-complete-code}

### File 1: `AndroidManifest.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/apk/res-auto">

    <!-- 
        PERMISSION EXPLANATIONS (in simple words):
        
        1. FOREGROUND_SERVICE — Allows our app to run a service that shows a 
           notification. Without this, Android won't let us run in the background.
        
        2. FOREGROUND_SERVICE_MEDIA_PLAYBACK — On Android 14+, you must specify 
           WHAT TYPE of foreground service you're running. We say "media playback" 
           even though we're not playing media — we're MONITORING media. This is 
           the closest type that fits our use case.
        
        3. FOREGROUND_SERVICE_SPECIAL_USE — Alternative type for Android 14+.
           Use this if "media playback" gets rejected.
        
        4. PACKAGE_USAGE_STATS — Lets us check WHICH app is currently in the 
           foreground (e.g., "Hotstar is on screen right now").
        
        5. POST_NOTIFICATIONS — On Android 13+, apps need explicit permission 
           to show notifications. Our foreground service needs a notification.
    -->

    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
    <uses-permission android:name="android.permission.PACKAGE_USAGE_STATS"
        tools:ignore="ProtectedPermissions" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    
    <!-- This tells Android: "Our app is for TV" -->
    <uses-feature
        android:name="android.software.leanback"
        android:required="true" />
    
    <!-- TV apps don't need touchscreen -->
    <uses-feature
        android:name="android.hardware.touchscreen"
        android:required="false" />

    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="TRP Tracker"
        android:supportsRtl="true"
        android:theme="@style/Theme.Leanback">

        <!-- 
            MAIN ACTIVITY — The screen the user sees when they open the app.
            It has a button to start/stop tracking.
            The LEANBACK_LAUNCHER intent-filter makes it show up on the 
            Android TV home screen.
        -->
        <activity
            android:name=".MainActivity"
            android:banner="@mipmap/ic_launcher"
            android:exported="true"
            android:screenOrientation="landscape">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LEANBACK_LAUNCHER" />
            </intent-filter>
        </activity>

        <!-- 
            MEDIA TRACKER SERVICE — This is the heart of our app.
            It runs in the background with a notification, watching what 
            other apps are playing.
            
            foregroundServiceType="specialUse" — tells Android this is a 
            special purpose service.
        -->
        <service
            android:name=".service.MediaTrackerService"
            android:exported="false"
            android:foregroundServiceType="specialUse">
            <property
                android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
                android:value="Media analytics tracking for TRP measurement" />
        </service>

        <!-- 
            NOTIFICATION LISTENER SERVICE — This is our "permission ticket."
            
            How it works:
            1. Android has a system where apps can say "I want to listen to 
               all notifications on this device"
            2. When you have this permission, you ALSO get the ability to 
               see all active MediaSessions
            3. The user must manually enable this in Settings → 
               Apps → Special access → Notification access
            
            Think of it like: You're applying for a "notification reader" 
            job. Once hired, you also get access to the media room.
            
            BIND_NOTIFICATION_LISTENER_SERVICE permission means ONLY the 
            Android system can bind to (connect to) this service. No other 
            app can misuse it.
        -->
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

### File 2: `MainActivity.kt`

**Location:** `app/src/main/java/com/yourcompany/trptracker/MainActivity.kt`

This is the screen the user sees. It has a big button to start tracking and shows status.

```kotlin
package com.yourcompany.trptracker

import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.yourcompany.trptracker.service.MediaTrackerService

/**
 * MainActivity — The launch screen of our TRP Tracker app.
 * 
 * WHAT THIS DOES (in simple words):
 * - Shows a "Start Tracking" button
 * - Shows a "Stop Tracking" button  
 * - Shows status text about what's happening
 * - Checks if all required permissions are granted
 * - If permissions are missing, guides the user to Settings
 * 
 * ON AN ANDROID TV:
 * - The user navigates with the TV remote (D-pad)
 * - Buttons are focused/clicked with the center button on remote
 * - There's no touchscreen
 */
class MainActivity : AppCompatActivity() {

    // TAG is like a label we put on our log messages
    // so we can filter them in Logcat
    companion object {
        private const val TAG = "TRP_MainActivity"
    }

    // UI elements
    private lateinit var statusText: TextView
    private lateinit var logText: TextView
    private lateinit var startButton: Button
    private lateinit var stopButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // We're building the UI in code (not XML) to keep this 
        // as a single self-contained file you can copy-paste.
        // In a real project, you'd use XML layouts.
        setupUI()
        
        Log.d(TAG, "MainActivity created")
    }

    override fun onResume() {
        super.onResume()
        // Every time the user comes back to this screen,
        // check if permissions are okay
        checkPermissions()
    }

    /**
     * Builds the UI programmatically.
     * 
     * Layout looks like:
     * ┌────────────────────────────┐
     * │     TRP Tracker v1.0       │
     * │                            │
     * │  [Start Tracking]          │
     * │  [Stop Tracking]           │
     * │                            │
     * │  Status: Running...        │
     * │                            │
     * │  --- Logs ---              │
     * │  Detected Hotstar playing  │
     * │  Title: Pushpa 2           │
     * │  ...                       │
     * └────────────────────────────┘
     */
    private fun setupUI() {
        val scrollView = ScrollView(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
        }

        // Title
        layout.addView(TextView(this).apply {
            text = "TRP Tracker v1.0"
            textSize = 28f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 32)
        })

        // Start Button
        startButton = Button(this).apply {
            text = "▶ Start Tracking"
            textSize = 20f
            setPadding(32, 16, 32, 16)
            // This is important for TV — makes the button focusable
            // by the D-pad remote
            isFocusable = true
            isFocusableInTouchMode = true
            setOnClickListener { startTracking() }
        }
        layout.addView(startButton, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        // Stop Button
        stopButton = Button(this).apply {
            text = "⏹ Stop Tracking"
            textSize = 20f
            setPadding(32, 16, 32, 16)
            isFocusable = true
            isFocusableInTouchMode = true
            setOnClickListener { stopTracking() }
        }
        layout.addView(stopButton, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        // Permission Buttons
        layout.addView(Button(this).apply {
            text = "⚙ Grant Notification Access"
            textSize = 16f
            isFocusable = true
            isFocusableInTouchMode = true
            setOnClickListener { openNotificationListenerSettings() }
        }, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        layout.addView(Button(this).apply {
            text = "⚙ Grant Usage Access"
            textSize = 16f
            isFocusable = true
            isFocusableInTouchMode = true
            setOnClickListener { openUsageAccessSettings() }
        }, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        // Status Text
        statusText = TextView(this).apply {
            text = "Status: Idle"
            textSize = 18f
            setPadding(0, 32, 0, 16)
        }
        layout.addView(statusText)

        // Log Text
        logText = TextView(this).apply {
            text = "--- Logs will appear here ---"
            textSize = 14f
            setPadding(0, 16, 0, 0)
            maxLines = 50
        }
        layout.addView(logText)

        scrollView.addView(layout)
        setContentView(scrollView)

        // Request focus on start button so TV remote can immediately use it
        startButton.requestFocus()
    }

    /**
     * Checks if all required permissions are granted.
     * 
     * We need TWO special permissions:
     * 
     * 1. NOTIFICATION LISTENER — So we can see other apps' MediaSessions.
     *    Without this, MediaSessionManager.getActiveSessions() returns 
     *    an empty list.
     * 
     * 2. USAGE STATS — So we can check which app is currently in the 
     *    foreground (e.g., "Hotstar is the active app right now").
     */
    private fun checkPermissions() {
        val notifOk = isNotificationListenerEnabled()
        val usageOk = isUsageAccessGranted()
        
        val status = buildString {
            append("Permissions Status:\n")
            append("  Notification Access: ${if (notifOk) "✅ GRANTED" else "❌ NOT GRANTED"}\n")
            append("  Usage Stats Access:  ${if (usageOk) "✅ GRANTED" else "❌ NOT GRANTED"}\n")
            
            if (!notifOk || !usageOk) {
                append("\n⚠️ Please grant missing permissions using the buttons above.")
            } else {
                append("\n✅ All permissions granted. Ready to track!")
            }
        }
        
        statusText.text = status
        Log.d(TAG, "Notification listener enabled: $notifOk, Usage access: $usageOk")
    }

    /**
     * Checks if our NotificationListenerService is enabled.
     * 
     * HOW THIS WORKS:
     * Android stores the list of enabled notification listeners in a 
     * system setting called "enabled_notification_listeners". It's a 
     * colon-separated list of ComponentNames like:
     * "com.app1/.Listener1:com.app2/.Listener2"
     * 
     * We check if our service's name is in that list.
     */
    private fun isNotificationListenerEnabled(): Boolean {
        val flat = Settings.Secure.getString(
            contentResolver,
            "enabled_notification_listeners"
        ) ?: return false
        
        val ourComponent = ComponentName(
            this, 
            "com.yourcompany.trptracker.service.TrpNotificationListenerService"
        ).flattenToString()
        
        return flat.contains(ourComponent)
    }

    /**
     * Checks if Usage Stats permission is granted.
     * 
     * HOW THIS WORKS:
     * UsageStatsManager needs the PACKAGE_USAGE_STATS permission.
     * But unlike normal permissions (where you get a popup), this one 
     * requires the user to go to Settings and manually flip a switch.
     * 
     * We check the status using AppOpsManager (App Operations Manager),
     * which tracks which special permissions are granted to which apps.
     */
    private fun isUsageAccessGranted(): Boolean {
        return try {
            val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
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

    /**
     * Opens the system Settings screen where the user can enable 
     * Notification Listener for our app.
     * 
     * On TV, this will open a Settings panel. The user uses the 
     * remote to navigate and enable our app.
     */
    private fun openNotificationListenerSettings() {
        try {
            startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
        } catch (e: Exception) {
            // Some TV devices might not have this settings page
            // In that case, try the general notification settings
            try {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            } catch (e2: Exception) {
                Toast.makeText(this, 
                    "Please enable notification access manually in Settings", 
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    /**
     * Opens the system Settings screen where the user can grant 
     * Usage Stats access to our app.
     */
    private fun openUsageAccessSettings() {
        try {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        } catch (e: Exception) {
            Toast.makeText(this, 
                "Please enable usage access manually in Settings", 
                Toast.LENGTH_LONG
            ).show()
        }
    }

    /**
     * Starts the tracking Foreground Service.
     * 
     * WHY startForegroundService()?
     * - On Android 8.0+ (Oreo), you CANNOT start a background service 
     *   using startService() if your app is in the background.
     * - startForegroundService() tells Android: "I'm about to start 
     *   a service that will show a notification (foreground service)."
     * - After calling this, the service has 5 seconds to call 
     *   startForeground() with a notification, otherwise Android 
     *   will crash the app.
     */
    private fun startTracking() {
        if (!isNotificationListenerEnabled()) {
            Toast.makeText(this, "Please enable Notification Access first!", Toast.LENGTH_LONG).show()
            return
        }

        val intent = Intent(this, MediaTrackerService::class.java)
        
        // ContextCompat.startForegroundService() handles the version check
        // internally — it calls startForegroundService() on Android 8+ 
        // and startService() on older versions.
        ContextCompat.startForegroundService(this, intent)
        
        statusText.text = "Status: ✅ Tracking started! You can now go watch something."
        Log.d(TAG, "Tracking started")
        
        Toast.makeText(this, "Tracking started! Go watch something on Hotstar/YouTube.", Toast.LENGTH_LONG).show()
    }

    /**
     * Stops the tracking service.
     */
    private fun stopTracking() {
        val intent = Intent(this, MediaTrackerService::class.java)
        stopService(intent)
        
        statusText.text = "Status: ⏹ Tracking stopped."
        Log.d(TAG, "Tracking stopped")
    }
}
```

---

### File 3: `TrpNotificationListenerService.kt`

**Location:** `app/src/main/java/com/yourcompany/trptracker/service/TrpNotificationListenerService.kt`

```kotlin
package com.yourcompany.trptracker.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

/**
 * TrpNotificationListenerService — Our "Permission Ticket" Service
 * 
 * WHAT IS THIS? (In the simplest terms):
 * 
 * Imagine there's a VIP club in Android called "MediaSession Readers Club."
 * To get into this club, you need a membership card.
 * 
 * That membership card is called "NotificationListenerService."
 * 
 * Android says: "If you're an approved notification listener, you also 
 * get to see all active MediaSessions." It's like buying a gym membership 
 * and also getting free access to the swimming pool.
 * 
 * DO WE ACTUALLY CARE ABOUT NOTIFICATIONS?
 * Not really! We only need this service to exist so that Android gives 
 * us the MediaSession reading power. But since we're implementing the 
 * service, we COULD also capture notification data as a bonus.
 * 
 * WHAT NOTIFICATIONS TELL US:
 * When Hotstar plays a video, it creates a media notification that shows:
 * - The title of what's playing
 * - Play/pause button
 * - Sometimes thumbnail
 * 
 * We log this as extra data, but our MAIN data comes from MediaSession 
 * (which is in the MediaTrackerService).
 * 
 * HOW THE USER ENABLES THIS:
 * Settings → Apps → Special access → Notification access → Enable "TRP Tracker"
 * (On some TVs: Settings → Security → Notification access)
 */
class TrpNotificationListenerService : NotificationListenerService() {

    companion object {
        private const val TAG = "TRP_NotifListener"
        
        // Package names of OTT apps we're interested in
        // You can add more as needed
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
            // Add more OTT packages as you discover them
        )
    }

    /**
     * Called when ANY app on the device posts a notification.
     * 
     * We filter to only care about OTT apps.
     * 
     * @param sbn StatusBarNotification — contains all info about the notification
     */
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return  // If null, do nothing
        
        val packageName = sbn.packageName
        
        // Only log notifications from OTT apps
        if (packageName in OTT_PACKAGES) {
            val extras = sbn.notification.extras
            
            // Extract whatever info the notification has
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

    /**
     * Called when a notification is removed/dismissed.
     * This could mean the user stopped playback or the app was closed.
     */
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
```

---

### File 4: `MediaTrackerService.kt` — THE HEART OF THE APP

**Location:** `app/src/main/java/com/yourcompany/trptracker/service/MediaTrackerService.kt`

This is the **most important file.** It does the actual tracking.

```kotlin
package com.yourcompany.trptracker.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Context
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
import kotlinx.coroutines.*

/**
 * MediaTrackerService — The Heart of Our TRP Tracker
 * 
 * WHAT THIS SERVICE DOES (in the simplest terms):
 * 
 * 1. Starts running in the background (as a Foreground Service with notification)
 * 2. Every few seconds, it asks Android: "Are there any apps playing media right now?"
 * 3. If yes, it reads ALL the information about what's playing:
 *    - What's the title? (e.g., "Pushpa 2 — The Rule")
 *    - Who's the artist/creator?
 *    - Is it playing or paused?
 *    - How long into the video/song are they?
 *    - What's the total duration?
 *    - Which app is playing it? (Hotstar? YouTube?)
 *    - What's the playback speed? (1x? 2x?)
 * 4. It also checks which app is currently on screen (foreground app)
 * 5. All this data is logged to Logcat (and later can be sent to server)
 * 
 * HOW IT READS OTHER APPS' MEDIA:
 * 
 * Step 1: Get the MediaSessionManager (the master list keeper)
 *         ↓
 * Step 2: Call getActiveSessions() to get all currently active media sessions
 *         ↓
 * Step 3: For each session, get its MediaController (the reader)
 *         ↓
 * Step 4: From MediaController, read:
 *         - .metadata → title, artist, album, duration
 *         - .playbackState → playing/paused, current position
 *         - .packageName → which app owns this session
 *         ↓
 * Step 5: Register a Callback on each MediaController to get 
 *         REAL-TIME updates (instant notifications when state changes)
 * 
 * TWO TRACKING STRATEGIES (we use BOTH):
 * 
 * Strategy A: POLLING (checking at regular intervals)
 *   - Every 5 seconds, we check: "What's playing right now?"
 *   - Good for: catching things we might have missed
 *   - Downside: 5 seconds is not instant
 * 
 * Strategy B: CALLBACKS (instant notifications)
 *   - We register listeners on each MediaController
 *   - When the state changes (play→pause, new song, etc.), 
 *     we get notified IMMEDIATELY
 *   - Good for: capturing exact moments of state changes
 *   - Downside: we might miss sessions that start between our polls
 * 
 * By using BOTH, we get comprehensive coverage.
 */
class MediaTrackerService : Service() {

    companion object {
        private const val TAG = "TRP_Tracker"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "trp_tracker_channel"
        
        // How often to poll for active sessions (in milliseconds)
        // 5000ms = 5 seconds — good balance between accuracy and battery
        private const val POLL_INTERVAL_MS = 5000L
        
        // How often to check the foreground app (in milliseconds)
        private const val FOREGROUND_CHECK_INTERVAL_MS = 3000L
    }

    // ────────────────────────────────────────────────
    // WHAT THESE VARIABLES ARE:
    // ────────────────────────────────────────────────
    
    // The master key to see all MediaSessions
    private var mediaSessionManager: MediaSessionManager? = null
    
    // Handler runs code on the main thread at scheduled times
    // Think of it as a "timer" that fires code repeatedly
    private val handler = Handler(Looper.getMainLooper())
    
    // Coroutine scope for background work
    // (like a lightweight thread manager)
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    // Keeps track of which MediaControllers we've already registered 
    // callbacks on, so we don't register twice
    // Key = package name of the app, Value = the callback we registered
    private val registeredCallbacks = mutableMapOf<String, MediaController.Callback>()
    
    // The current controllers we're watching
    private val activeControllers = mutableListOf<MediaController>()
    
    // Listener for when the list of active sessions changes
    // (e.g., a new app starts playing, or an app stops playing)
    private var sessionsChangedListener: MediaSessionManager.OnActiveSessionsChangedListener? = null

    // ════════════════════════════════════════════════
    // SERVICE LIFECYCLE METHODS
    // ════════════════════════════════════════════════

    /**
     * Called when the service is first created.
     * This is like the "constructor" of the service.
     */
    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "═══════════════════════════════════════")
        Log.i(TAG, "  TRP TRACKER SERVICE CREATED")
        Log.i(TAG, "═══════════════════════════════════════")
        
        // Step 1: Show the foreground notification
        // (Android requires this within 5 seconds of starting)
        startForegroundNotification()
        
        // Step 2: Get the MediaSessionManager
        mediaSessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) 
            as? MediaSessionManager
        
        if (mediaSessionManager == null) {
            Log.e(TAG, "❌ FATAL: Could not get MediaSessionManager! Is this a real Android device?")
            stopSelf()
            return
        }
        
        // Step 3: Start listening for session changes
        setupMediaSessionListener()
        
        // Step 4: Start the periodic polling
        startPolling()
        
        // Step 5: Do an immediate first scan
        scanActiveSessions()
    }

    /**
     * Called when someone calls startService() or startForegroundService().
     * 
     * START_STICKY means: "If Android kills this service due to low memory, 
     * restart it automatically when memory is available again."
     * This is important for our TRP tracker — we want it to keep running.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "Service onStartCommand called")
        return START_STICKY
    }

    /**
     * Called when the service is being destroyed (stopped).
     * We must clean up everything here to avoid memory leaks.
     */
    override fun onDestroy() {
        Log.i(TAG, "═══════════════════════════════════════")
        Log.i(TAG, "  TRP TRACKER SERVICE STOPPED")
        Log.i(TAG, "═══════════════════════════════════════")
        
        // Stop the timer
        handler.removeCallbacksAndMessages(null)
        
        // Cancel all background work
        serviceScope.cancel()
        
        // Unregister all callbacks
        cleanupCallbacks()
        
        // Remove session change listener
        sessionsChangedListener?.let {
            mediaSessionManager?.removeOnActiveSessionsChangedListener(it)
        }
        
        super.onDestroy()
    }

    /**
     * We don't support binding (another app connecting to this service).
     * This service is standalone.
     */
    override fun onBind(intent: Intent?): IBinder? = null

    // ════════════════════════════════════════════════
    // NOTIFICATION (Required for Foreground Service)
    // ════════════════════════════════════════════════

    /**
     * Creates and shows the foreground notification.
     * 
     * WHY IS THIS NEEDED?
     * Android says: "If you want to run a service for a long time, 
     * you MUST show a notification to the user. This prevents apps 
     * from secretly doing things in the background."
     * 
     * On TV, this notification exists but is mostly invisible to 
     * the user (no notification shade on most TVs).
     */
    private fun startForegroundNotification() {
        // Create notification channel (required on Android 8.0+)
        // Think of a channel as a "category" for notifications
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "TRP Tracking",                         // Name shown in settings
                NotificationManager.IMPORTANCE_LOW       // LOW = no sound, no popup
            ).apply {
                description = "Media tracking for TRP analytics"
                setShowBadge(false)  // Don't show a badge on the app icon
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }

        // Build the actual notification
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("TRP Tracker Active")
            .setContentText("Monitoring media playback...")
            .setSmallIcon(android.R.drawable.ic_media_play)  // Using system icon for demo
            .setPriority(NotificationCompat.PRIORITY_LOW)     // Don't disturb the user
            .setOngoing(true)                                  // Can't be swiped away
            .build()

        // This is THE critical call — it tells Android 
        // "this service is now a foreground service"
        startForeground(NOTIFICATION_ID, notification)
        
        Log.i(TAG, "✅ Foreground notification started")
    }

    // ════════════════════════════════════════════════
    // MEDIA SESSION MONITORING
    // ════════════════════════════════════════════════

    /**
     * Sets up a listener that fires WHENEVER the list of active 
     * MediaSessions changes.
     * 
     * For example:
     * - User opens Hotstar and starts a video → NEW session added → we get notified
     * - User closes YouTube → session removed → we get notified
     * 
     * HOW WE GET PERMISSION:
     * We pass our NotificationListenerService's ComponentName.
     * Android checks: "Is this app an approved notification listener?"
     * If yes → we get access. If no → SecurityException.
     */
    private fun setupMediaSessionListener() {
        try {
            // Create the ComponentName pointing to our NotificationListenerService
            val componentName = ComponentName(
                this,
                TrpNotificationListenerService::class.java
            )

            // Create the listener
            sessionsChangedListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
                // This fires every time the list of active sessions changes
                Log.i(TAG, "━━━ Active sessions changed! Found ${controllers?.size ?: 0} sessions ━━━")
                
                controllers?.let { 
                    handleActiveSessions(it) 
                }
            }

            // Register the listener
            mediaSessionManager?.addOnActiveSessionsChangedListener(
                sessionsChangedListener!!,
                componentName
            )

            Log.i(TAG, "✅ Session change listener registered")

        } catch (e: SecurityException) {
            // This happens when Notification Listener permission is not granted
            Log.e(TAG, "❌ SecurityException: Notification Listener not enabled!", e)
            Log.e(TAG, "   → User must go to Settings → Notification Access → Enable TRP Tracker")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error setting up session listener", e)
        }
    }

    /**
     * Polls (checks) for active sessions at regular intervals.
     * 
     * WHY POLL WHEN WE HAVE A LISTENER?
     * - The listener tells us when sessions CHANGE
     * - But we also want to periodically READ the current state 
     *   (position, play/pause) even if nothing "changed"
     * - A video that's been playing for 30 minutes hasn't "changed" 
     *   but we want to log that it's STILL playing and at what position
     */
    private fun startPolling() {
        handler.post(object : Runnable {
            override fun run() {
                scanActiveSessions()
                checkForegroundApp()
                
                // Schedule the next check
                handler.postDelayed(this, POLL_INTERVAL_MS)
            }
        })
    }

    /**
     * Scans all currently active MediaSessions and logs their state.
     * 
     * This is like walking through a TV station and checking every 
     * channel to see what's currently playing.
     */
    private fun scanActiveSessions() {
        try {
            val componentName = ComponentName(
                this,
                TrpNotificationListenerService::class.java
            )

            // Get ALL active media sessions on the device
            val controllers = mediaSessionManager?.getActiveSessions(componentName)
            
            if (controllers.isNullOrEmpty()) {
                // No media is playing on any app
                return
            }

            handleActiveSessions(controllers)

        } catch (e: SecurityException) {
            Log.e(TAG, "❌ Permission denied reading sessions. Is Notification Listener enabled?")
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning sessions", e)
        }
    }

    /**
     * Processes a list of active MediaControllers.
     * 
     * For each controller:
     * 1. Read all metadata (title, artist, etc.)
     * 2. Read playback state (playing, paused, position, etc.)
     * 3. Register a callback for real-time updates
     * 4. Log everything
     */
    private fun handleActiveSessions(controllers: List<MediaController>) {
        for (controller in controllers) {
            val packageName = controller.packageName ?: "unknown"
            
            // ──── READ METADATA ────
            // Metadata = information ABOUT the media (title, artist, album, etc.)
            // Not all apps provide all fields — some might be null
            val metadata = controller.metadata
            val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "Unknown Title"
            val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: ""
            val album = metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM) ?: ""
            val displayTitle = metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE) ?: ""
            val displaySubtitle = metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE) ?: ""
            val displayDescription = metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_DESCRIPTION) ?: ""
            val genre = metadata?.getString(MediaMetadata.METADATA_KEY_GENRE) ?: ""
            val mediaId = metadata?.getString(MediaMetadata.METADATA_KEY_MEDIA_ID) ?: ""
            
            // Duration in milliseconds (-1 means unknown)
            val durationMs = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: -1L
            val durationFormatted = if (durationMs > 0) formatDuration(durationMs) else "Unknown"
            
            // ──── READ PLAYBACK STATE ────
            // PlaybackState = what the player is DOING right now
            val playbackState = controller.playbackState
            val stateString = playbackStateToString(playbackState?.state)
            val positionMs = playbackState?.position ?: 0L
            val positionFormatted = formatDuration(positionMs)
            val playbackSpeed = playbackState?.playbackSpeed ?: 0f
            val lastUpdateTime = playbackState?.lastPositionUpdateTime ?: 0L
            
            // ──── READ EXTRAS ────
            // Some apps put additional data in "extras"
            val extras = playbackState?.extras
            val extrasInfo = if (extras != null) {
                extras.keySet()?.joinToString(", ") { key ->
                    "$key=${extras.get(key)}"
                } ?: "none"
            } else "none"
            
            // ──── LOG EVERYTHING ────
            Log.i(TAG, "┌──────────────────────────────────────────────")
            Log.i(TAG, "│ 📺 MEDIA SESSION DETECTED")
            Log.i(TAG, "│ App Package:       $packageName")
            Log.i(TAG, "│ Title:             $title")
            Log.i(TAG, "│ Display Title:     $displayTitle")
            Log.i(TAG, "│ Display Subtitle:  $displaySubtitle")
            Log.i(TAG, "│ Display Desc:      $displayDescription")
            Log.i(TAG, "│ Artist:            $artist")
            Log.i(TAG, "│ Album:             $album")
            Log.i(TAG, "│ Genre:             $genre")
            Log.i(TAG, "│ Media ID:          $mediaId")
            Log.i(TAG, "│ State:             $stateString")
            Log.i(TAG, "│ Position:          $positionFormatted ($positionMs ms)")
            Log.i(TAG, "│ Duration:          $durationFormatted ($durationMs ms)")
            Log.i(TAG, "│ Playback Speed:    ${playbackSpeed}x")
            Log.i(TAG, "│ Last Update:       $lastUpdateTime")
            Log.i(TAG, "│ Extras:            $extrasInfo")
            Log.i(TAG, "│ Timestamp:         ${System.currentTimeMillis()}")
            Log.i(TAG, "└──────────────────────────────────────────────")
            
            // ──── REGISTER CALLBACK (if not already registered) ────
            registerCallbackIfNeeded(controller, packageName)
        }
    }

    /**
     * Registers a callback on a MediaController so we get REAL-TIME 
     * notifications when the playback state changes.
     * 
     * Without this callback, we'd only know what's playing every 5 seconds 
     * (when our poll runs). With the callback, we know THE INSTANT 
     * something changes.
     * 
     * WHAT THE CALLBACK TELLS US:
     * - onPlaybackStateChanged → "The player went from PLAYING to PAUSED" 
     *                            or "The position jumped (user seeked)"
     * - onMetadataChanged → "A new video/song started playing"
     *                       or "The title/artist info was updated"
     * - onSessionDestroyed → "The app stopped its media session"
     *                        (usually means playback ended or app closed)
     */
    private fun registerCallbackIfNeeded(controller: MediaController, packageName: String) {
        // Don't register twice for the same app
        if (registeredCallbacks.containsKey(packageName)) return

        val callback = object : MediaController.Callback() {
            
            /**
             * Called when the playback state changes.
             * 
             * EXAMPLES OF WHEN THIS FIRES:
             * - User presses play → state changes to STATE_PLAYING
             * - User presses pause → state changes to STATE_PAUSED
             * - Video starts buffering → state changes to STATE_BUFFERING
             * - Video finishes → state changes to STATE_STOPPED
             * - User seeks (drags the progress bar) → position changes
             * - User changes speed (1x to 2x) → playbackSpeed changes
             */
            override fun onPlaybackStateChanged(state: PlaybackState?) {
                val stateStr = playbackStateToString(state?.state)
                val position = formatDuration(state?.position ?: 0)
                val speed = state?.playbackSpeed ?: 0f
                
                Log.i(TAG, "⚡ PLAYBACK STATE CHANGED [$packageName]")
                Log.i(TAG, "   New State: $stateStr")
                Log.i(TAG, "   Position: $position")
                Log.i(TAG, "   Speed: ${speed}x")
                Log.i(TAG, "   Timestamp: ${System.currentTimeMillis()}")
            }

            /**
             * Called when the metadata changes.
             * 
             * EXAMPLES OF WHEN THIS FIRES:
             * - A new video starts playing (title changes)
             * - The "Up Next" video auto-plays
             * - Album art is updated
             * - In music apps, the next song in the playlist starts
             */
            override fun onMetadataChanged(metadata: MediaMetadata?) {
                val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "Unknown"
                val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: ""
                val displayTitle = metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE) ?: ""
                val duration = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: -1L
                
                Log.i(TAG, "⚡ METADATA CHANGED [$packageName]")
                Log.i(TAG, "   New Title: $title")
                Log.i(TAG, "   Display Title: $displayTitle")
                Log.i(TAG, "   Artist: $artist")
                Log.i(TAG, "   Duration: ${formatDuration(duration)}")
                Log.i(TAG, "   Timestamp: ${System.currentTimeMillis()}")
            }

            /**
             * Called when the media session is destroyed.
             * This usually means the user stopped playback or closed the app.
             */
            override fun onSessionDestroyed() {
                Log.i(TAG, "⚡ SESSION DESTROYED [$packageName]")
                Log.i(TAG, "   The app stopped its media session")
                Log.i(TAG, "   Timestamp: ${System.currentTimeMillis()}")
                
                // Remove our callback since the session is gone
                registeredCallbacks.remove(packageName)
            }
        }

        try {
            controller.registerCallback(callback, handler)
            registeredCallbacks[packageName] = callback
            Log.i(TAG, "✅ Callback registered for: $packageName")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to register callback for $packageName", e)
        }
    }

    /**
     * Cleans up all registered callbacks.
     * Called when our service is stopping.
     */
    private fun cleanupCallbacks() {
        // We can't unregister callbacks from controllers we no longer have
        // references to, but clearing our map prevents memory leaks
        registeredCallbacks.clear()
        activeControllers.clear()
        Log.i(TAG, "Callbacks cleaned up")
    }

    // ════════════════════════════════════════════════
    // FOREGROUND APP DETECTION
    // ════════════════════════════════════════════════

    /**
     * Checks which app is currently in the foreground (visible on screen).
     * 
     * WHY DO WE NEED THIS?
     * MediaSession tells us WHAT is playing, but not necessarily 
     * if the user is LOOKING at it. For example:
     * - YouTube might be playing audio in the background while 
     *   the user is on the home screen
     * - We want to know: "Is the user actively watching Hotstar, 
     *   or is it playing in the background?"
     * 
     * HOW IT WORKS:
     * UsageStatsManager keeps a record of every app that was 
     * brought to the foreground. We query the most recent events 
     * (last 60 seconds) and find the last app that came to front.
     */
    private fun checkForegroundApp() {
        try {
            val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) 
                as? UsageStatsManager ?: return

            val currentTime = System.currentTimeMillis()
            
            // Query events from the last 60 seconds
            val usageEvents = usageStatsManager.queryEvents(
                currentTime - 60_000,  // 60 seconds ago
                currentTime             // now
            )

            var foregroundPackage: String? = null
            var foregroundActivity: String? = null

            // Walk through events and find the most recent 
            // "MOVE_TO_FOREGROUND" event
            val event = UsageEvents.Event()
            while (usageEvents.hasNextEvent()) {
                usageEvents.getNextEvent(event)
                
                // Event type 1 = MOVE_TO_FOREGROUND
                // (the app came to the front of the screen)
                if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                    foregroundPackage = event.packageName
                    foregroundActivity = event.className
                }
            }

            if (foregroundPackage != null) {
                // Only log if it's an OTT app (to avoid spam)
                if (foregroundPackage in TrpNotificationListenerService.OTT_PACKAGES) {
                    Log.i(TAG, "🖥️ FOREGROUND APP: $foregroundPackage")
                    Log.i(TAG, "   Activity: $foregroundActivity")
                }
            }

        } catch (e: SecurityException) {
            Log.e(TAG, "❌ Usage Stats permission not granted")
        } catch (e: Exception) {
            Log.e(TAG, "Error checking foreground app", e)
        }
    }

    // ════════════════════════════════════════════════
    // HELPER / UTILITY METHODS
    // ════════════════════════════════════════════════

    /**
     * Converts a PlaybackState integer code to a human-readable string.
     * 
     * These are the standard Android PlaybackState constants.
     * Every media app uses these same codes.
     */
    private fun playbackStateToString(state: Int?): String {
        return when (state) {
            PlaybackState.STATE_NONE -> "NONE (no media loaded)"
            PlaybackState.STATE_STOPPED -> "STOPPED"
            PlaybackState.STATE_PAUSED -> "PAUSED"
            PlaybackState.STATE_PLAYING -> "PLAYING"
            PlaybackState.STATE_FAST_FORWARDING -> "FAST_FORWARDING"
            PlaybackState.STATE_REWINDING -> "REWINDING"
            PlaybackState.STATE_BUFFERING -> "BUFFERING"
            PlaybackState.STATE_ERROR -> "ERROR"
            PlaybackState.STATE_CONNECTING -> "CONNECTING"
            PlaybackState.STATE_SKIPPING_TO_PREVIOUS -> "SKIPPING_TO_PREVIOUS"
            PlaybackState.STATE_SKIPPING_TO_NEXT -> "SKIPPING_TO_NEXT"
            PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM -> "SKIPPING_TO_QUEUE_ITEM"
            else -> "UNKNOWN ($state)"
        }
    }

    /**
     * Formats milliseconds into a human-readable time string.
     * Example: 3661000ms → "1:01:01" (1 hour, 1 minute, 1 second)
     */
    private fun formatDuration(ms: Long): String {
        if (ms < 0) return "Unknown"
        
        val seconds = (ms / 1000) % 60
        val minutes = (ms / (1000 * 60)) % 60
        val hours = ms / (1000 * 60 * 60)
        
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%d:%02d", minutes, seconds)
        }
    }
}
```

---

## 7. What Data Can We Actually Capture? {#7-data-we-can-capture}

Here's a realistic list of what data you can get from different apps:

### From MediaSession (Most Reliable)

| Data Field | Description | Available From |
|---|---|---|
| `METADATA_KEY_TITLE` | Video/song title | Most apps ✅ |
| `METADATA_KEY_ARTIST` | Artist/creator name | Music apps mostly |
| `METADATA_KEY_ALBUM` | Album name | Music apps mostly |
| `METADATA_KEY_DURATION` | Total duration in ms | Most apps ✅ |
| `METADATA_KEY_DISPLAY_TITLE` | Display-friendly title | Some apps |
| `METADATA_KEY_DISPLAY_SUBTITLE` | Subtitle text | Some apps |
| `METADATA_KEY_DISPLAY_DESCRIPTION` | Description text | Some apps |
| `METADATA_KEY_GENRE` | Genre of content | Some apps |
| `METADATA_KEY_MEDIA_ID` | Internal ID of the media | Some apps |
| `METADATA_KEY_ART` | Album art bitmap | Some apps |
| `METADATA_KEY_ART_URI` | URL to album art | Some apps |
| `PlaybackState.state` | Playing/Paused/Buffering etc. | All apps ✅ |
| `PlaybackState.position` | Current position in ms | All apps ✅ |
| `PlaybackState.playbackSpeed` | Speed (1x, 1.5x, 2x) | Most apps ✅ |
| `Controller.packageName` | Which app (com.hotstar.android) | Always ✅ |

### From UsageStats (Supplementary)

| Data | Description |
|---|---|
| Foreground package name | Which app is currently on screen |
| Foreground activity name | Which specific screen/page in the app |
| Time in foreground | How long the app has been on screen |

### Reality Check — What Different Apps Provide

**YouTube:** Usually provides title, channel name (as artist), and duration. Good metadata.

**Hotstar/Disney+:** Provides title and sometimes show/series name. Duration is usually available.

**Netflix:** Known to be more restrictive with metadata. May provide limited info.

**Amazon Prime Video:** Similar to Netflix — varies by content.

**Spotify (audio):** Excellent metadata — song title, artist, album, duration, art.

**JioCinema:** Varies — newer versions tend to have better MediaSession support.

### What We CANNOT Get (Honest Limitations)

- **Video resolution/quality** (720p, 1080p, 4K) — Not exposed via MediaSession
- **Bitrate** — Not exposed
- **Audio language/subtitle selection** — Not exposed
- **User account information** — Not accessible
- **Content URL/stream URL** — Not exposed (DRM protected)
- **Exact content ID** (Hotstar's internal content ID) — Sometimes in media_id, sometimes not
- **Whether content is live or VOD** — Not directly, but you can infer (if duration = -1, likely live)

---

## 8. Optimization Tips for TV {#8-optimization}

### Memory Optimization

```kotlin
// DO: Use lightweight data structures
data class TrpLogEntry(
    val timestamp: Long,
    val packageName: String,
    val title: String,
    val state: String,
    val positionMs: Long,
    val durationMs: Long
)

// DO: Write logs to file periodically, don't keep everything in memory
// Keep only the last 100 entries in memory, flush rest to file

// DON'T: Store Bitmap album art in memory (can be 500KB+ each!)
// If you need art, save the URI string, not the actual bitmap
```

### Polling Interval Tuning

```
5 seconds  → Good balance (recommended for start)
10 seconds → Lower battery/CPU usage, but might miss quick events
3 seconds  → More granular data but uses more resources
1 second   → Too aggressive for TV, might cause performance issues
```

### What NOT To Do On TV

1. **DON'T** create a wake lock — TVs don't sleep like phones
2. **DON'T** use AlarmManager for frequent polling — Handler is better for this
3. **DON'T** store bitmaps/images in memory — TV RAM is limited
4. **DON'T** do network calls on the main thread — use coroutines
5. **DON'T** log excessively in production — logging itself uses CPU

---

## 9. Testing & Debugging {#9-testing}

### ADB Commands for Testing

```bash
# See your logs (filter by our TAG)
adb logcat -s TRP_Tracker TRP_NotifListener TRP_MainActivity

# See ALL active media sessions on the device
adb shell dumpsys media_session

# See which app is in the foreground
adb shell dumpsys activity activities | grep mResumedActivity

# Check if your notification listener is enabled
adb shell settings get secure enabled_notification_listeners

# Check memory usage of your app
adb shell dumpsys meminfo com.yourcompany.trptracker

# Simulate a media button press (to test if sessions are active)
adb shell media dispatch play
adb shell media dispatch pause
```

### Testing Steps

1. **Install the app** on your Android TV (via ADB sideload or Android Studio)
2. **Open the app** → You'll see the main screen with buttons
3. **Grant permissions:**
    - Click "Grant Notification Access" → Enable in Settings
    - Click "Grant Usage Access" → Enable in Settings
4. **Click "Start Tracking"** → The foreground service starts
5. **Open Hotstar/YouTube** and play something
6. **Watch Logcat** — you should see logs appearing with the media info
7. **Try pausing, seeking, changing videos** — watch how callbacks fire

### Common Issues and Fixes

| Issue | Cause | Fix |
|---|---|---|
| Empty session list | Notification Listener not enabled | Go to Settings and enable it |
| SecurityException | Same as above | Enable Notification Listener |
| No foreground app data | Usage Stats not granted | Go to Settings and enable it |
| Service keeps dying | Low memory on TV | Reduce your app's memory usage |
| No metadata from an app | App doesn't set MediaSession metadata | Nothing you can do — app limitation |
| Callbacks not firing | Session was recreated with new token | Poll will catch the new session |

---

## 10. Limitations & Honest Reality Check {#10-limitations}

### What This Approach CAN Do
- Detect which OTT app is playing media
- Get the title, artist, and duration of what's playing
- Track play/pause/seek/skip events in real-time
- Measure how long a user watches content
- Detect when the user switches between apps
- Work on most Android TV devices (Android 5.0+)

### What This Approach CANNOT Do
- Get video quality/resolution information
- Get the actual stream URL (DRM protected)
- Get user account details from other apps
- Work if an app doesn't use MediaSession (rare but possible)
- Get detailed content metadata (show season, episode number, etc.) unless the app puts it in MediaSession
- Guarantee 100% data capture — some apps implement MediaSession lazily

### Next Steps (After Milestone 1)
Once logging works reliably, you can:
1. Store logs in a local database (Room)
2. Batch upload to your server every 5-15 minutes
3. Add device identification (TV model, Android version)
4. Add network info (WiFi SSID — to identify household)
5. Build a dashboard to visualize the TRP data

---

## Quick Reference: File Structure

```
app/
├── src/main/
│   ├── AndroidManifest.xml
│   └── java/com/yourcompany/trptracker/
│       ├── MainActivity.kt
│       └── service/
│           ├── MediaTrackerService.kt
│           └── TrpNotificationListenerService.kt
├── build.gradle
```

---

*Document generated for R&D purposes. All APIs used are from the official Android SDK documentation.*