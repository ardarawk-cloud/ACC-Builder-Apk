plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.offgrid.mesh"
    compileSdk = 35

    signingConfigs {
        create("offgridDev") {
            storeFile = file("../signing/offgrid-dev.jks")
            storePassword = "offgrid-dev-only"
            keyAlias = "offgriddev"
            keyPassword = "offgrid-dev-only"
        }
    }

    defaultConfig {
        applicationId = "com.offgrid.mesh.dev"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "0.1.0-phase1"
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("offgridDev")
        }
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
