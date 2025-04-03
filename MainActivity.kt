package com.example.batterytracker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private lateinit var batteryLevelTextView: TextView
    private lateinit var chargeCyclesTextView: TextView
    private lateinit var resetButton: Button
    
    private var currentCharge = 0.0
    private var lastBatteryLevel = -1
    private var savedChargeCycles = 0.0
    private var cumulativeChargePercentage = 0f
    private const val CUMULATIVE_CHARGE = "cumulative_charge"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // Initialize views
        batteryLevelTextView = findViewById(R.id.battery_level)
        chargeCyclesTextView = findViewById(R.id.charge_cycles)
        resetButton = findViewById(R.id.reset_button)
        
        // Load saved charge cycles
        savedChargeCycles = getSharedPreferences("battery_prefs", Context.MODE_PRIVATE)
            .getFloat("charge_cycles", 0.0).toDouble()
        cumulativeChargePercentage = getSharedPreferences("battery_prefs", Context.MODE_PRIVATE)
            .getFloat(CUMULATIVE_CHARGE, 0f)
        updateChargeCyclesDisplay()

        // Register battery broadcast receiver
        val batteryReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val batteryLevel: Int = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0)
                val status: Int = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                val isCharging: Boolean = status == BatteryManager.BATTERY_STATUS_CHARGING

                val lastLevel = lastBatteryLevel

                if (isCharging && lastLevel != -1 && batteryLevel > lastLevel) {
                    val chargedAmount = batteryLevel - lastLevel
                    cumulativeChargePercentage += chargedAmount

                    if (cumulativeChargePercentage >= 100f) {
                        val cyclesToAdd = cumulativeChargePercentage / 100f
                        savedChargeCycles += cyclesToAdd
                        cumulativeChargePercentage %= 100f
                    }

                    val prefs = context.getSharedPreferences("battery_prefs", Context.MODE_PRIVATE).edit()
                    prefs.putFloat("charge_cycles", savedChargeCycles.toFloat())
                    prefs.putFloat(CUMULATIVE_CHARGE, cumulativeChargePercentage)
                    prefs.apply()

                    updateChargeCyclesDisplay()
                }
                lastBatteryLevel = batteryLevel
                batteryLevelTextView.text = "Battery Level: ${batteryLevel}%"
            }
        }
        
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        registerReceiver(batteryReceiver, filter)
        
        // Reset button click listener
        resetButton.setOnClickListener {
            savedChargeCycles = 0.0
            currentCharge = 0.0
            updateChargeCyclesDisplay()
            getSharedPreferences("battery_prefs", Context.MODE_PRIVATE).edit()
                .putFloat("charge_cycles", 0.0f).apply()
        }
    }
    
    private fun updateChargeCyclesDisplay() {
        chargeCyclesTextView.text = "Charge Cycles: ${"%.2f".format(savedChargeCycles)}"
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Save charge cycles when activity is destroyed
        getSharedPreferences("battery_prefs", Context.MODE_PRIVATE).edit()
            .putFloat("charge_cycles", savedChargeCycles.toFloat()).apply()
    }
}