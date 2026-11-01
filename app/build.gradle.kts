plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.levit"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.levit"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            // Emulador falando com o backend da própria máquina. Pode ser trocado
            // dentro do app (toque longo no logo), só em debug.
            buildConfigField("String", "API_BASE_URL", "\"http://10.0.2.2:8080/api/v1/\"")
        }
        release {
            // Release só fala HTTPS e com o endereço definido no build:
            //   ./gradlew assembleRelease -Plevit.apiUrl=https://<host>/api/v1/
            val apiUrl = providers.gradleProperty("levit.apiUrl").getOrElse("")
            buildConfigField("String", "API_BASE_URL", "\"$apiUrl\"")
            optimization {
                enable = false
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    viewBinding {
        enable = true
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)

    // --- Auth: rede, corrotinas e armazenamento seguro ---
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.androidx.security.crypto)
    implementation(libs.gson)
    // Listas que crescem (itens, candidatos, lançamentos...): só desenha o que está na tela.
    implementation(libs.androidx.recyclerview)
}