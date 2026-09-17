plugins {
    alias(libs.plugins.android.library)
    id("maven-publish")
}

android {
    namespace = "com.realgear.readable_bottom_bar"
    compileSdk = 36

    defaultConfig {
        minSdk = 26

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
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
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildToolsVersion = "36.0.0"

    publishing {
        singleVariant("release") {
            withSourcesJar()
            withJavadocJar()
        }

        multipleVariants {
            withSourcesJar()
            withJavadocJar()
            allVariants()
        }
    }
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("maven") {
                groupId = "com.realgear"
                artifactId = "readable-bottom-bar"
                version = "1.1.0"

                from(components["release"])
            }
        }
    }
}

dependencies {
    implementation(libs.core.ktx)
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.navigation.fragment) // Using catalog version
    implementation("com.google.android.flexbox:flexbox:3.0.0")
    implementation(libs.navigation.ui) // Using catalog version

    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}