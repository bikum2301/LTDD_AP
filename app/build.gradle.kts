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

    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)

    // Retrofit, OkHttp, RecyclerView, Preferences (dùng chuỗi hoặc libs nếu có)
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.9.3") // Cân nhắc cập nhật
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.preference:preference-ktx:1.2.1")

    // **XÓA HOẶC COMMENT DÒNG NÀY:** Dependency tổng hợp cũ
    // implementation("com.google.android.exoplayer:exoplayer:2.18.1")

    // Glide
    implementation("com.github.bumptech.glide:glide:4.15.1") // Cân nhắc cập nhật
    // Dùng annotationProcessor cho Java, hoặc kapt cho Kotlin
    annotationProcessor("com.github.bumptech.glide:compiler:4.15.1")
    // Nếu dùng Kotlin:
    // kapt("com.github.bumptech.glide:compiler:4.15.1")

    // ExoPlayer dependencies - Sử dụng biến đã khai báo ở trên
    implementation("com.google.android.exoplayer:exoplayer-core:$exoplayerVersion")
    implementation("com.google.android.exoplayer:exoplayer-ui:$exoplayerVersion")

    // Thêm các module khác nếu cần
    implementation("com.google.android.exoplayer:exoplayer-dash:$exoplayerVersion")
    implementation("com.google.android.exoplayer:exoplayer-hls:$exoplayerVersion")
    implementation("com.google.android.exoplayer:exoplayer-smoothstreaming:$exoplayerVersion")
    implementation("com.facebook.shimmer:shimmer:0.5.0")
    implementation("de.hdodenhof:circleimageview:3.1.0")
    implementation("com.airbnb.android:lottie:6.4.0")
}