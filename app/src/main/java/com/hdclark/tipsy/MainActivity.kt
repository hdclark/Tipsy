package com.hdclark.tipsy

import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runCatching {
            setContentView(TipsyGameView(this))
        }.onFailure { error ->
            val message = "Tipsy could not start (${error::class.java.simpleName}). Please report this."
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            setContentView(TextView(this).apply { text = message })
        }
    }
}
