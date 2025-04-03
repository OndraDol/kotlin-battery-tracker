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
import android.content.BroadcastReceiver
import android.util.Log
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList
import android.widget.ImageButton
import androidx.appcompat.app.AlertDialog
import com.google.android.material.switchmaterial.SwitchMaterial
import androidx.appcompat.app.AppCompatDelegate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {
    companion object {
        private const val PREF_NAME = "BatteryInfo"
        private const val CHARGE_CYCLES = "chargeCycles"
        private const val DARK_MODE = "darkMode"
        private const val NOTIFICATIONS = "notifications"
        private const val FLOATING_WINDOW = "floatingWindow"
        private const val OVERHEATING_ALARM = "overheatingAlarm"
        private const val FAHRENHEIT = "fahrenheit"
    }

    private lateinit var sharedPreferences: SharedPreferences
    //private lateinit var binding: ActivityMainBinding
    private lateinit var batteryLevelText: TextView
    private lateinit var chargeCyclesText: TextView
    private lateinit var statusText: TextView
    private lateinit var healthText: TextView
    private lateinit var powerText: TextView
    private lateinit var voltageText: TextView
    private lateinit var temperatureText: TextView
    private lateinit var levelText: TextView
    private lateinit var levelChart: LineChart
    private lateinit var tempChart: LineChart
    private lateinit var settingsButton: ImageButton
    private lateinit var settingsButton: ImageButton

    private val batteryLevelEntries = ArrayList<Entry>()
    private val temperatureEntries = ArrayList<Entry>()
    private var lastUpdateTime: Long = 0
    private var chargeCycles: Float = 0.0f
    private val scope = CoroutineScope(Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        sharedPreferences = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

        // Set Dark Mode
        if (sharedPreferences.getBoolean(DARK_MODE, false)) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }

        // Initialize other preferences
        val notificationsEnabled = sharedPreferences.getBoolean(NOTIFICATIONS, true)
        val floatingWindowEnabled = sharedPreferences.getBoolean(FLOATING_WINDOW, false)
        val overheatingAlarmEnabled = sharedPreferences.getBoolean(OVERHEATING_ALARM, true)
        val fahrenheitEnabled = sharedPreferences.getBoolean(FAHRENHEIT, false)

        Log.d("Settings", "Notifications: $notificationsEnabled, Floating Window: $floatingWindowEnabled, Overheating Alarm: $overheatingAlarmEnabled, Fahrenheit: $fahrenheitEnabled")

        batteryLevelText = findViewById(R.id.battery_level)
        chargeCyclesText = findViewById(R.id.charge_cycles)
        statusText = findViewById(R.id.tvStatus)
        healthText = findViewById(R.id.tvHealth)
        powerText = findViewById(R.id.tvPower)
        voltageText = findViewById(R.id.tvVoltage)
        temperatureText = findViewById(R.id.tvTemperature)
        levelText = findViewById(R.id.tvLevel)
        levelChart = findViewById(R.id.levelChart)
        tempChart = findViewById(R.id.tempChart)
        settingsButton = findViewById(R.id.settings_button)

        val resetButton = findViewById<Button>(R.id.reset_button)

        // Get battery info
        val batteryStatus = applicationContext.registerReceiver(null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED))

        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPct = if (level != -1 && scale != -1) level * 100 / scale.toFloat() else 0f

        scope.launch {
            chargeCycles = withContext(Dispatchers.IO) {
                sharedPreferences.getFloat(CHARGE_CYCLES, 0.0f)
            }

            // Calculate charge cycles based on battery percentage change
            val batteryPctChange = level.toFloat()
            chargeCycles += batteryPctChange / 100f

            Log.d("ChargeCycles", "chargeCycles: $chargeCycles")
            withContext(Dispatchers.IO) {
                sharedPreferences.edit().putFloat(CHARGE_CYCLES, chargeCycles).apply()
            }
        }
        sharedPreferences.edit().putFloat(CHARGE_CYCLES, chargeCycles).apply()

        // Initialize with actual values
        updateBatteryInfo(batteryPct.toInt(), chargeCycles)

        // resetButton.setOnClickListener { // Removed reset button
        //     // Reset charge cycles and initial level
        //     scope.launch {
        //         withContext(Dispatchers.IO) {
        //             sharedPreferences.edit().putFloat(CHARGE_CYCLES, 0.0f).apply()
        //         }
        //         updateBatteryInfo(0, 0.0f)
        //     }
        // }

        // Register Battery Change Receiver
        val batteryIntentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        registerReceiver(batteryReceiver, batteryIntentFilter)

        initCharts()

        settingsButton.setOnClickListener {
            showSettingsDialog()
        }
    }

    private fun showSettingsDialog() {
        val builder = AlertDialog.Builder(this)
        val inflater = layoutInflater
        val dialogLayout = inflater.inflate(R.layout.dialog_settings, null)
        
        val darkModeSwitch = dialogLayout.findViewById<SwitchMaterial>(R.id.dark_theme_switch)
        val notificationsSwitch = dialogLayout.findViewById<SwitchMaterial>(R.id.notifications_switch)
        val floatingWindowSwitch = dialogLayout.findViewById<SwitchMaterial>(R.id.floating_window_switch)
        val overheatingAlarmSwitch = dialogLayout.findViewById<SwitchMaterial>(R.id.overheating_alarm_switch)
        val fahrenheitSwitch = dialogLayout.findViewById<SwitchMaterial>(R.id.fahrenheit_switch)

        darkModeSwitch.isChecked = sharedPreferences.getBoolean(DARK_MODE, false)
        notificationsSwitch.isChecked = sharedPreferences.getBoolean(NOTIFICATIONS, true)
        floatingWindowSwitch.isChecked = sharedPreferences.getBoolean(FLOATING_WINDOW, false)
        overheatingAlarmSwitch.isChecked = sharedPreferences.getBoolean(OVERHEATING_ALARM, true)
        fahrenheitSwitch.isChecked = sharedPreferences.getBoolean(FAHRENHEIT, false)

        builder.setView(dialogLayout)
        val alertDialog = builder.create()
        alertDialog.show()

        darkModeSwitch.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit().putBoolean(DARK_MODE, isChecked).apply()
            if (isChecked) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            }
            recreate()
        }

        notificationsSwitch.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit().putBoolean(NOTIFICATIONS, isChecked).apply()
        }

        floatingWindowSwitch.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit().putBoolean(FLOATING_WINDOW, isChecked).apply()
        }

        overheatingAlarmSwitch.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit().putBoolean(OVERHEATING_ALARM, isChecked).apply()
        }

        fahrenheitSwitch.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit().putBoolean(FAHRENHEIT, isChecked).apply()
        }

        // Save initial charge cycles
        /*dialogLayout.findViewById<Button>(R.id.save_button).setOnClickListener {
            val initialCyclesInput = dialogLayout.findViewById<TextInputEditText>(R.id.etInitialCycles).text.toString()
            try {
                val initialCycles = initialCyclesInput.toFloat()
                sharedPreferences.edit().putFloat(CHARGE_CYCLES, initialCycles).apply()
                chargeCycles = initialCycles
                updateBatteryInfo(levelText.text.toString().replace("Level: ", "").replace("%", "").toInt(), chargeCycles) // Update displayed charge cycles
                alertDialog.dismiss()
            } catch (e: NumberFormatException) {
                // Handle invalid input, maybe show an error message
                Log.e("SettingsDialog", "Invalid charge cycles input: ${e.message}")
            }
        }*/
    }
    }

    private fun initCharts() {
        // Initialize Level Chart
        val levelDataSet = LineDataSet(batteryLevelEntries, "Battery Level")
        levelDataSet.color = getColor(R.color.purple_500)
        levelDataSet.fillColor = getColor(R.color.purple_200)
        levelDataSet.setDrawFilled(true)
        val levelData = LineData(levelDataSet)
        levelChart.data = levelData
        levelChart.description.isEnabled = false
        levelChart.legend.isEnabled = false
        levelChart.xAxis.labelRotationAngle = 0f
        levelChart.invalidate()
        levelChart.setBackgroundColor(getColor(R.color.cardview_dark_background)) // Example background color
        levelChart.setGridBackgroundColor(getColor(R.color.cardview_shadow_end_color)) // Example grid color

        // Initialize Temperature Chart
        val tempDataSet = LineDataSet(temperatureEntries, "Temperature")
        tempDataSet.fillColor = getColor(R.color.teal_700)
        tempDataSet.lineWidth = 2f // Set line thickness
        tempDataSet.color = getColor(R.color.teal_200)
        val tempData = LineData(tempDataSet)
        tempDataSet.setDrawFilled(true)
        tempDataSet.setDrawCircles(true) // Enable circles
        tempDataSet.circleRadius = 3f // Adjust circle radius
        tempDataSet.setCircleColor(getColor(R.color.white)) // Circle color
        tempDataSet.setDrawCircleHole(true) // Enable circle hole
        tempDataSet.circleHoleColor = getColor(R.color.cardview_dark_background) // Hole color
        tempChart.setBackgroundColor(getColor(R.color.cardview_dark_background)) // Example background color
        tempChart.legend.textColor = getColor(R.color.white) // Legend text color
        tempChart.xAxis.textColor = getColor(R.color.white) // X-Axis text color
        tempChart.data = tempData
        tempChart.setGridBackgroundColor(getColor(R.color.cardview_shadow_end_color)) // Example grid color

        // X-Axis formatting for time
        tempChart.xAxis.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                return sdf.format(Date(value.toLong()))
            }
        }
        tempChart.xAxis.textColor = getColor(R.color.white) // Example axis text color

        // Y-Axis formatting for temperature (°C)
        tempChart.axisLeft.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                return "$value°C"
            }
        }
        tempChart.axisLeft.textColor = getColor(R.color.white) // Example axis text color
        tempChart.axisRight.isEnabled = false // Disable right Y-axis
        tempChart.setGridBackgroundColor(getColor(R.color.cardview_shadow_end_color)) // Example grid color
        tempChart.invalidate()
    }

    val remainingTimeText = findViewById<TextView>(R.id.remaining_time)
    val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    val chargeTimeRemaining = batteryManager.computeChargeTimeRemaining()

    val remainingTimeString = if (chargeTimeRemaining > 0) {
        val hours = chargeTimeRemaining / 3600000
        val minutes = (chargeTimeRemaining % 3600000) / 60000
        getString(R.string.remaining_time_format, hours, minutes)
    } else {
        getString(R.string.remaining_time_na)
    }

    remainingTimeText.text = remainingTimeString
    private val batteryReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val status: Int = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val health: Int = intent.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)
            val plugged: Int = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
            val voltage: Int = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)
            val temperature: Int = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)
            val level: Int = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale: Int = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val batteryPct = level * 100 / scale.toFloat()

            val statusString = when (status) {
                BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
                BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
                BatteryManager.BATTERY_STATUS_FULL -> "Full"
                BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not Charging"
                else -> "Unknown"
            }

            val healthString = when (health) {
                BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
                BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
                BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
                BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over Voltage"
                BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Unspecified Failure"
                else -> "Unknown"
            }

            val pluggedString = when (plugged) {
                BatteryManager.BATTERY_PLUGGED_AC -> "AC"
                BatteryManager.BATTERY_PLUGGED_USB -> "USB"
                BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
                else -> "Battery"
            }

            val temperatureC = temperature.toFloat() / 10
            val voltageMV = voltage

            statusText.text = "Status: $statusString"
            healthText.text = "Health: $healthString"
            powerText.text = "Power Source: $pluggedString"
            voltageText.text = "Voltage: ${voltageMV}mV"
            val temperatureFahrenheit = (temperatureC * 9/5) + 32
            temperatureText.text = if (sharedPreferences.getBoolean(FAHRENHEIT, false)) "Temperature: ${String.format("%.1f", temperatureFahrenheit)} °F" else "Temperature: ${temperatureC} °C"
            levelText.text = "Level: ${batteryPct}%"

            Log.d("Battery", "Level: ${batteryPct}%")

            val currentTime = System.currentTimeMillis()
            if (currentTime - lastUpdateTime > 1000) {
                lastUpdateTime = currentTime
                addChartEntry(batteryPct, temperatureC)
            }
        }
        if (status == BatteryManager.BATTERY_STATUS_CHARGING) {
            scope.launch {
                chargeCycles += batteryPct / 100f
                withContext(Dispatchers.IO) {
                    sharedPreferences.edit().putFloat(CHARGE_CYCLES, chargeCycles).apply()
                }
            }
        }
    }

    private fun addChartEntry(batteryPct: Float, temperatureC: Float) {
        val time = System.currentTimeMillis().toFloat()

        batteryLevelEntries.add(Entry(time, batteryPct))
        temperatureEntries.add(Entry(time, temperatureC))

        // Notify data set changed
        (levelChart.data.getDataSetByIndex(0) as LineDataSet).notifyDataSetChanged()
        (tempChart.data.getDataSetByIndex(0) as LineDataSet).notifyDataSetChanged()

        levelChart.data.notifyDataChanged()
        tempChart.data.notifyDataChanged()

        // Refresh chart
        tempChart.invalidate()

        // Limit data points to 200
        if (batteryLevelEntries.size > 200) {
            batteryLevelEntries.removeAt(0)
        }
        if (temperatureEntries.size > 200) {
            temperatureEntries.removeAt(0)
        }
    }

    private fun updateBatteryInfo(level: Int, cycles: Float) {
        batteryLevelText.text = "Battery Level: $level%"
        chargeCyclesText.text = "Charge Cycles: ${String.format("%.2f", cycles)}"
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(batteryReceiver)
    }
}