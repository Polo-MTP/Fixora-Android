package com.fixora.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/** Punto de entrada de Hilt: a partir de aquí se crean las dependencias de toda la app. */
@HiltAndroidApp
class FixoraApplication : Application()
