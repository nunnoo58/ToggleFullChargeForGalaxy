plugins {
    id("com.android.application")
}

android {
    namespace = "io.github.nunnoo58.fullcharge"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.nunnoo58.fullcharge"
        minSdk = 34
        targetSdk = 36
        versionCode = 3
        versionName = "1.2"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        // WRITE_SECURE_SETTINGS는 adb로 직접 부여하므로 경고 무시
        disable += "ProtectedPermissions"
    }
}

// 외부 라이브러리 없음: 프레임워크 API만 사용
