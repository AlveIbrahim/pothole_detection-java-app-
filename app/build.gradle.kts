plugins {
    id("com.android.application")
}

android {
    namespace = "com.example.potholedetector"
    compileSdk = 33

    defaultConfig {
        applicationId = "com.example.potholedetector"
        minSdk = 24
        targetSdk = 33
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Enable MultiDex if needed
        multiDexEnabled = true
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

    // Update Java compatibility to a newer version
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    // Configure source sets to include jniLibs folder
    sourceSets {
        getByName("main") {
            jniLibs.srcDirs("src/main/jniLibs")
            // Add custom resource directory for OpenCV resources
            res.srcDirs("src/main/res", "src/main/res-opencv")
        }
    }

    // Don't compress native libraries
    androidResources {
        noCompress += listOf("so", "pt") // Don't compress native libraries and PyTorch model files
    }

    // Fix packaging options
    packaging {
        resources {
            excludes += listOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/license.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/notice.txt",
                "META-INF/ASL2.0",
                "META-INF/*.kotlin_module"
            )
        }

        jniLibs {
            useLegacyPackaging = true
        }
    }

    // Limit ABI to reduce APK size
    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a")
            isUniversalApk = false
        }
    }
}

// Fix Kotlin dependency conflicts
configurations.all {
    resolutionStrategy {
        // Force consistent Kotlin version
        force("org.jetbrains.kotlin:kotlin-stdlib:1.8.10")
        force("org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.8.10")
        force("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.8.10")

        // Exclude older versions
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk7")
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk8")
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("com.google.android.material:material:1.9.0")
    implementation("androidx.activity:activity:1.7.2")

    // MultiDex support - explicitly included
    implementation("androidx.multidex:multidex:2.0.1")

    // PyTorch Mobile for model inference - update to match versions
    implementation("org.pytorch:pytorch_android:1.13.1")
    implementation("org.pytorch:pytorch_android_torchvision:1.13.1")

    // For video processing
    implementation("androidx.media:media:1.6.0")

    // For UI features
    implementation("androidx.cardview:cardview:1.0.0")
    implementation("androidx.recyclerview:recyclerview:1.3.1")

    // For file operations
    implementation("commons-io:commons-io:2.11.0")

    // Force consistent Kotlin version
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.8.10")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}