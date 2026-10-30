import java.io.StringReader
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// Variables de entorno por flavor: app/env/<flavor>.properties (se suben a Git, sin secretos).
// Cada desarrollador puede sobrescribir un valor en local.properties con el prefijo del flavor,
// por ejemplo: dev.API_URL=http://192.168.1.50:3000/
fun readProperties(file: RegularFile): Properties = Properties().apply {
    providers.fileContents(file).asText.orNull?.let { load(StringReader(it)) }
}

val localProperties = readProperties(rootProject.layout.projectDirectory.file("local.properties"))

fun envProperties(flavor: String): Map<String, String> {
    val env = readProperties(layout.projectDirectory.file("env/$flavor.properties"))
    return env.stringPropertyNames().associateWith { key ->
        localProperties.getProperty("$flavor.$key") ?: env.getProperty(key)
    }
}

android {
    namespace = "com.fixora.app"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.fixora.app"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    flavorDimensions += "env"
    productFlavors {
        create("dev") {
            dimension = "env"
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
            resValue("string", "app_name", "Fixora Dev")
        }
        create("prod") {
            dimension = "env"
            resValue("string", "app_name", "Fixora")
        }
    }
    productFlavors.configureEach {
        envProperties(name).forEach { (key, value) ->
            buildConfigField("String", key, "\"$value\"")
        }
    }

    buildTypes {
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
        // Igual que release (optimizada), pero firmada con la llave de debug para instalarla en un
        // celular. Sirve para juzgar animaciones y rendimiento; debug es mucho más lenta. No se publica.
        create("profile") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
        resValues = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
