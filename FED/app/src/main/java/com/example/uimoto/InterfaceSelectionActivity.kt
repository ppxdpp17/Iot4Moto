package com.example.uimoto

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class InterfaceSelectionActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.interface_selection)

        // Button for Interface 1
        findViewById<Button>(R.id.buttonInterface1).setOnClickListener {
            val intent = Intent(this, MainInterface1Activity::class.java)
            startActivity(intent)
        }

        // Button for Interface 2
        findViewById<Button>(R.id.buttonInterface2).setOnClickListener {
            val intent = Intent(this, MainInterface2Activity::class.java)
            startActivity(intent)
        }
    }
}
