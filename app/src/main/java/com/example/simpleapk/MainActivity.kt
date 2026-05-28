package com.example.simpleapk

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private lateinit var emulatorView: EmulatorView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val emulator = IbmPc5150Emulator()
        emulator.bootFromAssets(assets)

        emulatorView = findViewById(R.id.emulatorView)
        emulatorView.attach(emulator)
    }

    override fun onResume() {
        super.onResume()
        if (::emulatorView.isInitialized) {
            emulatorView.start()
        }
    }

    override fun onPause() {
        if (::emulatorView.isInitialized) {
            emulatorView.stop()
        }
        super.onPause()
    }
}
