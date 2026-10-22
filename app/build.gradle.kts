import java.io.StringReader
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
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
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
