package Utils

import androidx.test.core.app.ApplicationProvider

actual class ContextProvider {
    actual companion object {
        actual fun getContext(): Any? {
            return ApplicationProvider.getApplicationContext()
        }
    }
}