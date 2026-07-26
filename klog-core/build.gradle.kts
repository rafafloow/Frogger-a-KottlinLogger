plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "dev.rafaflow.klog.core"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        minSdk = 21
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    // Available only when the host application already uses OkHttp.
    compileOnly(libs.okhttp)
    testImplementation(libs.junit)
}
