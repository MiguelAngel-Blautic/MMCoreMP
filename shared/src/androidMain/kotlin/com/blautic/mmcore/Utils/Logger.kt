package Utils

import android.util.Log

actual object Logger{
    actual fun log(tipo: Int, tag: String, message: String){
        when(tipo){
            1 -> Log.d(tag, message)
            2 -> Log.e(tag, message)
        }
    }
}