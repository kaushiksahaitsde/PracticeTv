plugins {
    alias(libs.plugins.android.application)
}

android {
    // namespace = the package for the generated R class.
    // Keep matching the source code package so R.layout.* etc. work without changes.
    namespace = "com.example.mytvxml"

    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        // Different applicationId so both :app and :apptv can be installed side-by-side.
        applicationId = "com.example.mytvxml.tv"
        minSdk = 21
        targetSdk = 36
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

    sourceSets {
        getByName("main") {
            // Reuse the movies.json from :app/assets during migration.
            // Once stable, copy movies.json into apptv/src/main/assets/ and remove this.
            assets.srcDirs("${rootProject.projectDir}/app/src/main/assets")

            // Reuse mipmap launcher icons from :app during migration.
            // Once stable, copy the mipmap-* folders into apptv/src/main/res/ and remove this.
            res.srcDirs(
                "${rootProject.projectDir}/app/src/main/res/mipmap-hdpi",
                "${rootProject.projectDir}/app/src/main/res/mipmap-mdpi",
                "${rootProject.projectDir}/app/src/main/res/mipmap-xhdpi",
                "${rootProject.projectDir}/app/src/main/res/mipmap-xxhdpi",
                "${rootProject.projectDir}/app/src/main/res/mipmap-xxxhdpi",
                "src/main/res"  // keep the module's own res directory
            )
        }
    }
}

dependencies {
    // Shared tracker library (MediaTrackerService + NLS + MediaBrowserExplorer)
    implementation(project(":tracker"))

    // TV-specific UI
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.leanback)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.fragment)
    implementation(libs.glide)
    implementation(libs.glide.v4120)
    implementation(libs.gson)
}
