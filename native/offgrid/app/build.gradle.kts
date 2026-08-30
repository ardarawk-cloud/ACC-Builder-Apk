plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.offgrid.mesh"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.offgrid.mesh"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "0.1.0-phase1"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib:2.0.21")
}
