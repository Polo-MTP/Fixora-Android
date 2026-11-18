package com.fixora.app

import android.app.Application
import com.fixora.app.core.notifications.DeviceTokenRegistrar
import com.fixora.app.core.notifications.PushSetup
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/** Punto de entrada de Hilt: a partir de aquí se crean las dependencias de toda la app. */
@HiltAndroidApp
class FixoraApplication : Application() {

    @Inject lateinit var deviceTokenRegistrar: DeviceTokenRegistrar

    override fun onCreate() {
        super.onCreate()
        // Notificaciones push (ANDROID-6): Firebase, canal y registro del token con la sesión.
        PushSetup.initialize(this)
        deviceTokenRegistrar.start()
    }
}
