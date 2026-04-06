plugins {
    alias(libs.plugins.android.library)
}

android {
    // Namespace for the generated R class (no resources in this library, but required by AGP)
    namespace = "com.example.mytvxml.tracker"

    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        minSdk = 21
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    // Core AndroidX
    implementation(libs.androidx.core.ktx)

    // MediaBrowserCompat — needed for MediaBrowserExplorer (Option 3, zero-permission approach)
    implementation(libs.androidx.media)
}
