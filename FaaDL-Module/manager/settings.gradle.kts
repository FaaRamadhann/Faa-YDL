pluginManagement {
    repositories {
        google()
        // Maven Central mirror di GCS: repo1.maven.org diblokir (403) di jaringan ini.
        maven("https://maven-central.storage-download.googleapis.com/maven2")
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        maven("https://maven-central.storage-download.googleapis.com/maven2")
    }
}

rootProject.name = "FYDLManager"
