plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.lsparanoid) apply false
}
}

lsparanoid {
    // Seed fixo garante builds estáveis, determinísticos e compatíveis com Configuration Cache
    seed = 114514

    // Filtro de classes para ofuscação de strings:
    // Protege todos os pacotes do app e do Bcore, evitando processamento desnecessário de bibliotecas externas de sistema
    classFilter = { className ->
        className.startsWith("com.th.thtestes") || className.startsWith("top.niunaijun.blackbox")
    }

    // Processar dependências internas/módulos incluídos
    includeDependencies = true

    // Boas práticas: habilitar em builds de release (e opcionalmente em debug com -PobfuscateDebug=true)
    variantFilter = { variant ->
        variant.buildType == "release" || project.hasProperty("obfuscateDebug")
    }
}


android {
    namespace = "com.th.thtestes"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.th.thtestes"
        minSdk = 24
        targetSdk = 37
        versionCode = 133
        versionName = "1.3.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures {
        compose = true
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
            isDebuggable = false
        }
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

dependencies {
    implementation(project(":Bcore"))
    compileOnly(libs.lsparanoid.core)
    implementation("com.android.tools.build:apksig:8.7.0")

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestRuntimeOnly(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
