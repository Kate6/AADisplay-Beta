package io.github.nitsuya.aa.display.xposed

import de.robv.android.xposed.XposedBridge
import io.github.nitsuya.aa.display.util.LogBuffer

fun log(tag: String, message: String) {
    XposedBridge.log("[$tag] $message")
    LogBuffer.write('I', tag, message)
}

fun log(tag: String, message: String, t: Throwable?) {
    XposedBridge.log("[$tag] $message")
    LogBuffer.write('E', tag, "$message ${t?.message ?: ""}")
    if(t != null){
        XposedBridge.log(t)
    }
}

fun logw(tag: String, message: String) {
    XposedBridge.log("[$tag] $message")
    LogBuffer.write('W', tag, message)
}

fun loge(tag: String, message: String, t: Throwable? = null) {
    XposedBridge.log("[$tag] $message")
    LogBuffer.write('E', tag, "$message ${t?.message ?: ""}")
    if (t != null) XposedBridge.log(t)
}