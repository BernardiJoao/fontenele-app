plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// Endereço do servidor: dá para passar na hora de gerar o APK, sem editar este arquivo
//   ./gradlew assembleDebug -PurlServidor=https://fontenele-servidor.onrender.com/
// (é o que fazem o gerar-apk.bat e o botão "Gerar APK" do GitHub). Sem isso, vale o padrão abaixo.
val urlServidor: String = (project.findProperty("urlServidor") as String?)
    ?.trim()?.takeIf { it.isNotEmpty() }
    ?.let { if (it.endsWith("/")) it else "$it/" }
    ?: "http://10.0.2.2:8080/"

android {
    namespace = "br.com.fontenele.orcamentos"
    compileSdk = 35

    defaultConfig {
        applicationId = "br.com.fontenele.orcamentos"
        minSdk = 29          // Android 10 ou superior
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        // ====================================================================================
        // ENDEREÇO DO SERVIDOR (banco de dados unificado entre todos os celulares)
        // Todo celular que instalar o app já sai conectado neste endereço (dá para mudar no app,
        // em Configurações). Exemplos:
        //   emulador + servidor no mesmo computador ..... http://10.0.2.2:8080/
        //   celular na mesma rede Wi-Fi do computador ... http://192.168.0.10:8080/  (IP do computador)
        //   servidor na internet (Render) ............... https://fontenele-servidor.onrender.com/  (veja o README)
        // Troque o padrão no "val urlServidor" lá em cima ou passe -PurlServidor=... ao gerar o APK.
        // ====================================================================================
        buildConfigField("String", "URL_SERVIDOR", "\"$urlServidor\"")
    }

    // Chave de assinatura fixa (só para testes): o APK gerado em qualquer computador ou no GitHub
    // tem a mesma assinatura, então dá para instalar uma versão nova por cima da anterior.
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storeType = "pkcs12"
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
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
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.fragment.ktx)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.navigation.compose)

    // Armazenamento interno: banco SQLite via Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Recurso físico: leitor biométrico
    implementation(libs.androidx.biometric)

    // Armazenamento externo: sincronização com o backend Spring Boot / PostgreSQL
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)

    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
}
