// Khai báo biến version ở đây bằng 'val' (Kotlin)
val exoplayerVersion = "2.19.1" // Hoặc phiên bản mới nhất bạn muốn dùng

plugins {
    alias(libs.plugins.android.application)
    // Nếu bạn dùng Kotlin trong code Java, bạn cần thêm plugin kapt
    // id("kotlin-kapt") // Bỏ comment nếu bạn dùng Kotlin và cần kapt cho Glide
}

android {
    namespace = "com.example.streamapp"
    compileSdk = 35 // Giảm xuống 34 để khớp với targetSdk, trừ khi bạn thực sự cần API 35

    defaultConfig {
        applicationId = "com.example.streamapp"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false // Cú pháp Kotlin DSL
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        // Đảm bảo bạn thực sự cần Java 11, nếu không VERSION_1_8 phổ biến hơn
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    // Nếu dùng Kotlin, thêm khối kotlinOptions:
    // kotlinOptions {
    //    jvmTarget = "11" // Hoặc "1.8"
    // }
    buildFeatures {
        viewBinding = true // Cú pháp Kotlin DSL
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.6.1") // Hoặc phiên bản mới hơn
    implementation("com.google.android.material:material:1.12.0") // Hoặc phiên bản mới hơn
    implementation("androidx.constraintlayout:constraintlayout:2.1.4") // Hoặc phiên bản mới hơn
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0") // Cho Pull-to-Refresh

    // ViewModel and LiveData (Nếu bạn dùng cho quản lý trạng thái phức tạp hơn)
    // implementation 'androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0'
    // implementation 'androidx.lifecycle:lifecycle-livedata-ktx:2.7.0'

    // Retrofit & Gson (Cho Networking)
    implementation("com.squareup.retrofit2:retrofit:2.9.0") // Hoặc 2.11.0
    implementation("com.squareup.retrofit2:converter-gson:2.9.0") // Hoặc 2.11.0
    implementation("com.google.code.gson:gson:2.10.1") // Hoặc phiên bản mới hơn
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0") // Hoặc phiên bản OkHttp3 mới nhất

    // Glide (Cho tải ảnh)
    implementation("com.github.bumptech.glide:glide:4.16.0") // Hoặc phiên bản mới nhất
    annotationProcessor("com.github.bumptech.glide:compiler:4.16.0")

    // CircleImageView (Cho ảnh đại diện tròn)
    implementation("de.hdodenhof:circleimageview:3.1.0")

    // Lottie (Cho animation ở WelcomeActivity)
    implementation("com.airbnb.android:lottie:6.1.0") // Hoặc phiên bản mới nhất

    // ExoPlayer (Kiểm tra và sử dụng phiên bản mới nhất ổn định)
    implementation("com.google.android.exoplayer:exoplayer-core:2.19.1")
    implementation("com.google.android.exoplayer:exoplayer-ui:2.19.1")
    implementation("com.google.android.exoplayer:extension-mediasession:2.19.1")

    // SharedPreferences (đã có sẵn trong Android SDK, nhưng nếu dùng androidx.preference)
    implementation("androidx.preference:preference-ktx:1.2.1")


    // Test Implementations
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}