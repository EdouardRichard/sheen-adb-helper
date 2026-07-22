plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.sheen.adbhelper"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.sheen.adbhelper"
        minSdk = 30
        targetSdk = 36
        versionCode = 2
        versionName = "0.0.2"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    buildTypes {
        debug {
            isDebuggable = true
        }
        release {
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.all { it.useTestNG() }
    }

    packaging {
        resources.excludes += setOf(
            "META-INF/AL2.0",
            "META-INF/LGPL2.1",
            "META-INF/versions/9/OSGI-INF/MANIFEST.MF",
        )
    }
}

configurations.configureEach {
    exclude(group = "androidx.profileinstaller", module = "profileinstaller")
}

dependencies {
    implementation(project(":core:adb"))
    implementation(project(":core:data"))
    implementation(project(":core:ui"))
    implementation(project(":feature:devices"))
    implementation(project(":feature:overview"))
    implementation(project(":feature:shell"))
    implementation(project(":feature:processes"))
    implementation(project(":feature:logcat"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:apps"))
    implementation(project(":feature:files"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.testng)
}
