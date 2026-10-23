plugins {
    id("com.android.application") version "8.7.3"
}

android {
    namespace = "com.faa.faadlmod"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.faa.faadlmod"
        minSdk = 24
        targetSdk = 34
        versionCode = 10
        versionName = "1.0.9"
    }

    // Pakai keystore yang sama dengan build lama: update APK wajib key sama.
    signingConfigs {
        create("fydl") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "fydlmod"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("fydl")
        }
    }

    sourceSets["main"].apply {
        manifest.srcFile("AndroidManifest.xml")
        java.srcDir("src")
        res.srcDir("res")
        assets.srcDir("assets")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    packaging {
        // Modul mengeksekusi binary dari APK manager; jangan di-strip/align ulang.
        jniLibs.useLegacyPackaging = true
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

// Nama APK tetap manager.apk; zip.py membaca outputs/apk/release/ juga.
androidComponents {
    onVariants(selector().withBuildType("release")) { variant ->
        variant.outputs.forEach { out ->
            (out as com.android.build.api.variant.impl.VariantOutputImpl).outputFileName
                .set("manager.apk")
        }
    }
}
