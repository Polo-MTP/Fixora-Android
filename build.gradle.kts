// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}

// Activa los hooks de Git versionados en .githooks (como Husky en npm install).
// Corre al sincronizar Gradle; si no hay Git o no es un repositorio, no hace nada.
runCatching {
    providers.exec {
        commandLine("git", "config", "core.hooksPath", ".githooks")
        isIgnoreExitValue = true
    }.result.get()
}