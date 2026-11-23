package com.ambiback.manager

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application class — required entry point for Hilt dependency injection.
 * Referenced in AndroidManifest.xml via android:name=".AmbiBackManagerApp"
 */
@HiltAndroidApp
class AmbiBackManagerApp : Application()
