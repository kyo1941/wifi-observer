plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.ktlint)
}

kotlin {
    jvmToolchain(11)

    android {
        namespace = "com.example.wifi_observer.shared"
        compileSdk = 36
        minSdk = 24

        // commonTest を JVM 上で実行するホスト(ユニット)テストを有効化する
        withHostTestBuilder {
        }
    }

    sourceSets {
        commonMain.dependencies {
            // domain の公開 API が Flow を露出するため api で再公開する
            api(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
