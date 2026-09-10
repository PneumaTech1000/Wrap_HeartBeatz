plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.giga.tech1000.sound_engine"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
        // Enable NDK build
        externalNativeBuild {
            cmake {
                cppFlags.add("")
            }
        }
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    externalNativeBuild {
        cmake {
            path = file("src/main/CMakeLists.txt")
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            // Note: Removed isDebuggable as it's causing unresolved reference
            // This property might not be available or is deprecated in newer AGP
        }
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)

    // DSPark dependency - header-only library
    // Option 1: If integrating as source
    // implementation project(path: ':lib:dspark')
    //
    // Option 2: If using prebuilt (we'll handle via CMake)
    // No Gradle dependency needed for header-only, handled in CMake
}