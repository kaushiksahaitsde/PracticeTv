plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.mytvxml"

    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
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
            assets.srcDirs("${rootProject.projectDir}/app/src/main/assets")
            res.srcDirs(
                "${rootProject.projectDir}/app/src/main/res/mipmap-hdpi",
                "${rootProject.projectDir}/app/src/main/res/mipmap-mdpi",
                "${rootProject.projectDir}/app/src/main/res/mipmap-xhdpi",
                "${rootProject.projectDir}/app/src/main/res/mipmap-xxhdpi",
                "${rootProject.projectDir}/app/src/main/res/mipmap-xxxhdpi",
                "src/main/res"
            )
        }
    }
}

dependencies {
    implementation(project(":tracker"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.leanback)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.fragment)
    implementation(libs.glide)
    implementation(libs.glide.v4120)
    implementation(libs.gson)
}
