package Utils

import platform.Foundation.NSLog

actual object Logger {
    actual fun log(tipo: Int, tag: String, message: String) {
        when(tipo){
            1 -> NSLog("$tag: $message")
            2 -> NSLog("ERROR $tag: $message")
        }
    }
}