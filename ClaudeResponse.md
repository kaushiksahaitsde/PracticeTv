Let me analyze everything — what Cursor gave you, what we discovered is broken, and give you the final clean version.

---

## Issues found in Cursor's output:

| Problem | Where |
|---|---|
| `Dadb.pair()` doesn't exist in dadb library | `AdbManager.kt` |
| `.toString()` instead of `.output` on shell response | `AdbManager.kt` |
| Wrong dependency `com.github.mobile-dev-inc:dadb:1.2.7` (JitPack) | `core/build.gradle.kts` |
| JitPack not needed — dadb is on Maven Central now | `settings.gradle.kts` |
| `compileSdk { version = release(36) }` — this is wrong Gradle syntax | both `build.gradle.kts` files |
| Missing `kotlinOptions` block | `core/build.gradle.kts` |

---

## Final Correct Files — Copy paste each one:

### 1. `settings.gradle.kts`

```kotlin
pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // JitPack NOT needed — dadb is on Maven Central
    }
}

rootProject.name = "MyTvXml"

include(":tracker")
include(":app")
include(":apptv")
include(":core")
include(":companion")
```

---

### 2. `core/build.gradle.kts`

```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.example.mytvxml.core"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    // ✅ Correct Maven Central coordinate — no JitPack needed
    api("dev.mobile:dadb:1.2.10")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("androidx.core:core-ktx:1.12.0")
}
```

---

### 3. `core/src/main/java/com/example/mytvxml/core/AdbManager.kt`

```kotlin
package com.example.mytvxml.core

import dadb.Dadb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object AdbManager {

    // TV app applicationId = com.example.mytvxml.tv
    private const val LISTENER_COMPONENT =
        "com.example.mytvxml.tv/com.example.mytvxml.service.TrpNotificationListenerService"

    /**
     * Connect to FireStick and grant notification listener permission.
     *
     * WHY no pairing here:
     * FireStick uses old-style TCP ADB on port 5555.
     * Pairing (Wireless Debugging pairing code) is only for Android 11+
     * phone-to-phone connections — NOT for FireStick ADB over network.
     * So we just connect directly with IP + port. No pairing step needed.
     *
     * @param ip       FireStick IP address shown in its Network Settings
     * @param adbPort  Default 5555 — FireStick ADB always listens here
     */
    suspend fun grantNotificationListener(
        ip: String,
        adbPort: Int = 5555
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            Dadb.create(ip, adbPort).use { adb ->

                // Grant the permission
                adb.shell("cmd notification allow_listener $LISTENER_COMPONENT")

                // ✅ use .output — NOT .toString()
                // .toString() prints the object reference, not the actual text
                val verifyRaw = adb.shell(
                    "settings get secure enabled_notification_listeners"
                ).output

                if (!verifyRaw.contains("com.example.mytvxml.tv")) {
                    return@withContext Result.failure(
                        Exception("Grant sent but permission not confirmed. Please try again.")
                    )
                }
            }
            Result.success("Notification listener enabled successfully!")

        } catch (e: Exception) {
            Result.failure(Exception("Failed to connect to FireStick: ${e.message}", e))
        }
    }
}
```

---

### 4. `companion/build.gradle.kts`

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.example.mytvxml.companion"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.mytvxml.companion"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    implementation(project(":core"))
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
}
```

---

### 5. `companion/src/main/AndroidManifest.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />

    <application
        android:allowBackup="true"
        android:icon="@android:drawable/sym_def_app_icon"
        android:label="TRP Companion"
        android:supportsRtl="true"
        android:theme="@style/Theme.MaterialComponents.DayNight.NoActionBar">

        <activity
            android:name=".SetupActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

    </application>
</manifest>
```

---

### 6. `companion/src/main/res/layout/activity_setup.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<ScrollView xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:padding="20dp">

        <TextView
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:text="TRP Companion Setup"
            android:textStyle="bold"
            android:textSize="22sp" />

        <TextView
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="8dp"
            android:text="Make sure your phone and FireStick are on the same WiFi. Enter the FireStick IP address from its Network Settings."
            android:textSize="14sp" />

        <!-- Only IP needed — no pairing required for FireStick -->
        <EditText
            android:id="@+id/etIp"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="16dp"
            android:hint="Fire TV IP (e.g. 172.16.6.153)"
            android:inputType="text" />

        <EditText
            android:id="@+id/etAdbPort"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="8dp"
            android:hint="ADB Port (default 5555)"
            android:inputType="number" />

        <com.google.android.material.button.MaterialButton
            android:id="@+id/btnEnable"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="16dp"
            android:text="Enable Listener" />

        <ProgressBar
            android:id="@+id/progress"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_gravity="center_horizontal"
            android:layout_marginTop="16dp"
            android:visibility="gone" />

        <TextView
            android:id="@+id/tvStatus"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="16dp"
            android:textSize="14sp" />

    </LinearLayout>
</ScrollView>
```

---

### 7. `companion/src/main/java/com/example/mytvxml/companion/SetupActivity.kt`

```kotlin
package com.example.mytvxml.companion

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.mytvxml.core.AdbManager
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch

class SetupActivity : AppCompatActivity() {

    private lateinit var etIp: EditText
    private lateinit var etAdbPort: EditText
    private lateinit var btnEnable: MaterialButton
    private lateinit var progress: ProgressBar
    private lateinit var tvStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)

        etIp      = findViewById(R.id.etIp)
        etAdbPort = findViewById(R.id.etAdbPort)
        btnEnable = findViewById(R.id.btnEnable)
        progress  = findViewById(R.id.progress)
        tvStatus  = findViewById(R.id.tvStatus)

        // Pre-fill default port
        etAdbPort.setText("5555")

        btnEnable.setOnClickListener {
            val ip = etIp.text.toString().trim()

            if (ip.isBlank()) {
                tvStatus.text = "Please enter the FireStick IP address."
                return@setOnClickListener
            }

            val adbPort = etAdbPort.text.toString().toIntOrNull() ?: 5555

            setLoading(true)
            tvStatus.text = "Connecting to FireStick..."

            lifecycleScope.launch {
                val result = AdbManager.grantNotificationListener(ip, adbPort)

                setLoading(false)

                tvStatus.text = if (result.isSuccess) {
                    "✅ Success! TRP is ready to track media."
                } else {
                    "❌ Failed: ${result.exceptionOrNull()?.message}"
                }

                if (result.isSuccess) {
                    // Save so TV app knows permission is granted
                    getSharedPreferences("trp_prefs", MODE_PRIVATE)
                        .edit()
                        .putBoolean("permission_granted", true)
                        .apply()

                    btnEnable.isEnabled = false
                    btnEnable.text = "Permission Enabled ✓"
                }
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        progress.visibility = if (loading) View.VISIBLE else View.GONE
        btnEnable.isEnabled = !loading
    }
}
```

---

## What changed from Cursor's version — quick summary:

| File | What was fixed |
|---|---|
| `settings.gradle.kts` | Removed JitPack |
| `core/build.gradle.kts` | Fixed compileSdk syntax, correct `dev.mobile:dadb:1.2.10`, added `kotlinOptions` |
| `AdbManager.kt` | Removed `pairIfNeeded()` entirely, fixed `.output` bug, simplified to just `grantNotificationListener()` |
| `companion/build.gradle.kts` | Fixed compileSdk syntax, added `kotlinOptions` |
| `activity_setup.xml` | Removed pairing fields (pair port, pair code) — not needed for FireStick |
| `SetupActivity.kt` | Removed pairing logic, simplified to just IP + port |