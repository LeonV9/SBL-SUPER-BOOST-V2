package com.sbl.superboost

import android.media.AudioManager
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.sbl.superboost.ui.MainScreen
import com.sbl.superboost.ui.SblTheme

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Hardware-Lautstärketasten steuern immer die Medienlautstärke
        volumeControlStream = AudioManager.STREAM_MUSIC
        setContent {
            SblTheme {
                MainScreen(vm)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.syncFromSystem()
        vm.refreshBluetooth()
    }

    /**
     * Solange die App im Vordergrund ist, steuern die Lautstärketasten im Bereich
     * 100-200 % die digitale Verstärkung. Bis 100 % übernimmt Android selbst die Regelung
     * (der Regler wird per ContentObserver synchronisiert).
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP && vm.volume >= 100) {
            vm.stepVolume(5)
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN && vm.volume > 100) {
            vm.stepVolume(-5)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if ((keyCode == KeyEvent.KEYCODE_VOLUME_UP && vm.volume >= 100) ||
            (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN && vm.volume > 100)
        ) {
            return true
        }
        return super.onKeyUp(keyCode, event)
    }
}
