import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.objectbox)
}

android {
    namespace = "com.mdiwebma.diffviewer"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.mdiwebma.diffviewer"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }


    val localProperties = Properties()
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localProperties.load(FileInputStream(localPropertiesFile))
    }

    val signPassword = localProperties.getProperty("sign.password") ?: ""
    val hasSigning = signPassword.isNotEmpty() && file("diffviewer-app.jks").exists()

    signingConfigs {
        if (hasSigning) {
            create("release") {
                storeFile = file("diffviewer-app.jks")
                storePassword = signPassword
                keyAlias = "diffviewer"
                keyPassword = signPassword
            }
        }
    }

    buildTypes {
        debug {
            //signingConfig = signingConfigs.getByName("release")
        }
        release {
            if (hasSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    applicationVariants.all {
        if (buildType.name == "release") {
            outputs.all {
                val outputImpl = this as? com.android.build.gradle.internal.api.BaseVariantOutputImpl
                outputImpl?.outputFileName = "DiffViewer-v${defaultConfig.versionName}(${defaultConfig.versionCode})-release.apk"
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    implementation(project(":diffview"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.google.material)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.viewpager2)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    releaseImplementation(libs.objectbox.android)
    debugImplementation(libs.objectbox.android.objectbrowser)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}