package dev.pdfforge

import android.app.Application
import android.os.StrictMode
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class PdfForgeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Phase 0: INFRA-06 — StrictMode for Debug (log only; penaltyDeath causes crashes
        // when system code does disk access on main thread, e.g. IdsController.getIdsSharedPreference)
        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .build()
            )
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder()
                    .detectLeakedSqlLiteObjects()
                    .detectLeakedClosableObjects()
                    .penaltyLog()
                    .build()
            )
        }
    }
}
