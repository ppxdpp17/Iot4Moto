plugins {
    id("com.android.library") //!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
    id("org.jetbrains.kotlin.android")//!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
}


android {
    namespace = "com.example.testvalues"
    compileSdk = 34

    defaultConfig {
        //applicationId = "com.example.testvalues"!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!! (Comentei isto)
        minSdk = 24
        targetSdk = 34
        //versionCode = 1!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!! (Comentei isto)
        //versionName = "1.0"!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!! (Comentei isto)

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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}