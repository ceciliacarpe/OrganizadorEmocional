
plugins {
    id("com.android.application")
    alias(libs.plugins.kotlin.android)
    id("com.google.gms.google-services")

}

android {
    namespace = "com.example.organizadoremocional"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.organizadoremocional"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }

    packagingOptions {
        resources {
            excludes += listOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt"
                )
            }
        }

}

dependencies {

    //Firebase
    implementation(platform("com.google.firebase:firebase-bom:33.13.0"))
    implementation("com.google.firebase:firebase-analytics")
    implementation ("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx")

    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    //Lifecycle y UI
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.credentials:credentials:1.5.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.5.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")


    //Lottie (animaciones)
    implementation("com.airbnb.android:lottie:6.6.6")

    //Google iniciar sesión
    implementation("com.google.android.gms:play-services-auth:20.6.0")

    //API Google Calendar
    implementation ("com.google.api-client:google-api-client-android:1.33.0")
    implementation ("com.google.api-client:google-api-client-gson:1.33.0")
    implementation ("com.google.http-client:google-http-client-android:1.40.1")
    implementation ("com.google.http-client:google-http-client-gson:1.40.1")
    implementation ("com.google.apis:google-api-services-calendar:v3-rev411-1.25.0")

    implementation ("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.6.4")


    implementation ("com.github.AnyChart:AnyChart-Android:1.1.5")


    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.firebase.auth.ktx)
    implementation(libs.play.services.fitness)
    implementation(libs.androidx.work.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    implementation("com.github.PhilJay:MPAndroidChart:3.1.0")
}

