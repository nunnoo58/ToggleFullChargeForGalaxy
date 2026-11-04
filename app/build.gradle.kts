plugins {
    id("com.android.application")
}

// 버전명은 여기서만 바꾸면 APK 파일명에도 반영됨 (예: ToggleFullChargeForGalaxy-v2.1-debug.apk)
val appVersionName = "2.1"
val appVersionCode = 12

android {
    namespace = "app.togglefullcharge"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.togglefullcharge"
        minSdk = 34
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName
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

base {
    archivesName.set("ToggleFullChargeForGalaxy-v$appVersionName")
}

// 외부 라이브러리 없음: 프레임워크 API만 사용
