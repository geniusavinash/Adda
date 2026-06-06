package co.mobilise.adda

import android.app.Application

/**
 * App entry point. Exposes a process-wide context so singletons added in later
 * steps (LlmService in Step 2, the Ktor server in Step 4) can reach files/dirs
 * without threading a Context everywhere.
 */
class AddaApp : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: AddaApp
            private set
    }
}
