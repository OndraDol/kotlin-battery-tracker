package com.example.batterytracker

import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.batterytracker.R
import android.content.Context
import android.content.SharedPreferences

class MainActivity : AppCompatActivity() {
    private val PREF_NAME = "BatteryInfo"
    private val INITIAL_LEVEL = "initialLevel"
    private val CHARGE_CYCLES = "chargeCycles"
    private val LOW_THRESHOLD = 20
    private val HIGH_THRESHOLD = 80

    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var batteryLevelText: TextView
    private lateinit var chargeCyclesText: TextView
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        sharedPreferences = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        
        batteryLevelText = findViewById(R.id.battery_level)
        chargeCyclesText = findViewById(R.id.charge_cycles)
        val resetButton = findViewById<Button>(R.id.reset_button)
        
        // Get battery info
        val batteryStatus = applicationContext.registerReceiver(null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED))

        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPct = if (level != -1 && scale != -1) level * 100 / scale.toFloat() else 0f

        val initialLevel = sharedPreferences.getInt(INITIAL_LEVEL, -1)
        var chargeCycles = sharedPreferences.getFloat(CHARGE_CYCLES, 0.0f)

        if (initialLevel == -1) {
            // First time, store the initial battery level
            sharedPreferences.edit().putInt(INITIAL_LEVEL, level).apply()
        } else {
            // Check for charge cycle completion
            if (initialLevel != -1 && initialLevel < LOW_THRESHOLD && level > HIGH_THRESHOLD) {
                chargeCycles += 1.0f
            }
        }

        // Save current level as initial level for the next check
        // Save current level as initial level for the next check
        if (initialLevel >= LOW_THRESHOLD || level <= HIGH_THRESHOLD) {
            sharedPreferences.edit().putInt(INITIAL_LEVEL, level).apply()
        }

        // Initialize with actual values
        updateBatteryInfo(batteryPct.toInt(), chargeCycles)
        
        resetButton.setOnClickListener {
            // Reset charge cycles and initial level
            sharedPreferences.edit().putFloat(CHARGE_CYCLES, 0.0f).putInt(INITIAL_LEVEL, -1).apply()
            updateBatteryInfo(0, 0.0f)
        }
    }
    
    private fun updateBatteryInfo(level: Int, cycles: Float) {
        batteryLevelText.text = "Battery Level: $level%"
        chargeCyclesText.text = "Charge Cycles: ${String.format("%.2f", cycles)}"
    }
}