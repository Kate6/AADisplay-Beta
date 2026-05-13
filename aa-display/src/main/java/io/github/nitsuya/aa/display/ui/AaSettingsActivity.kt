package io.github.nitsuya.aa.display.ui

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.topjohnwu.superuser.Shell

class AaSettingsActivity : AppCompatActivity() {
    companion object {
        const val TAG = "AADisplay_AaSettings"
        const val AA_SETTINGS_PKG = "com.google.android.projection.gearhead"
        const val AA_SETTINGS_CLS = "com.google.android.projection.gearhead.companion.settings.Material3SettingsActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Shell.getShell().isRoot) {
            Log.e(TAG, "No root access")
            Toast.makeText(this, "Root required to launch AA settings", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        Shell.cmd("am start -n $AA_SETTINGS_PKG/$AA_SETTINGS_CLS").exec()
        Log.i(TAG, "Launched Android Auto settings via root shell")
        finish()
    }
}
