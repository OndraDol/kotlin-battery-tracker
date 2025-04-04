package com.example.batterytracker

import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Bundle
import android.widget.TextView
import androidx.core.content.ContextCompat
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
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.android.material.textfield.TextInputEditText
import com.example.batterytracker.databinding.ActivityMainBinding

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
    private lateinit var binding: ActivityMainBinding
    //private lateinit var settingsButton: ImageButton // This is not needed with ViewBinding

    private val batteryLevelEntries = ArrayList<Entry>()
    private val temperatureEntries = ArrayList<Entry>()
    private var lastUpdateTime: Long = 0
    private var chargeCycles: Float = 0.0f
    private var cumulativeChargePercentage: Float = 0.0f
    private var previousBatteryPct: Float = 0.0f
    private val scope = CoroutineScope(Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initializePreferences()
        registerBatteryReceiver()
        initCharts()
        setupSettingsButton()
        updateRemainingTime()
    }

    private fun initializePreferences() {
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
    }

    private fun registerBatteryReceiver() {
        val batteryStatus = applicationContext.registerReceiver(null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val batteryPct = if (batteryStatus != null) {
            val level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            if (level != -1 && scale != -1) level * 100 / scale.toFloat() else 0f
        } else {
            0f
        }

        scope.launch {
            chargeCycles = withContext(Dispatchers.IO) {
                sharedPreferences.getFloat(CHARGE_CYCLES, 0.0f)
            }

            // Calculate charge cycles based on battery percentage change
            val batteryPctChange = batteryPct - previousBatteryPct
            cumulativeChargePercentage += batteryPctChange
            chargeCycles = cumulativeChargePercentage / 100f

            Log.d("ChargeCycles", "chargeCycles: $chargeCycles, cumulativeChargePercentage: $cumulativeChargePercentage")
            withContext(Dispatchers.IO) {
                sharedPreferences.edit().putFloat(CHARGE_CYCLES, chargeCycles).apply()
                sharedPreferences.edit().putFloat("cumulativeChargePercentage", cumulativeChargePercentage).apply()
            }
        }
        // Initialize with actual values
        updateBatteryInfo(batteryPct.toInt(), chargeCycles)

        // Register Battery Change Receiver
        val batteryIntentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        registerReceiver(batteryReceiver, batteryIntentFilter)
    }

    private fun setupSettingsButton() {
        binding.settingsButton.setOnClickListener {
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
        val initialCyclesInput = dialogLayout.findViewById<TextInputEditText>(R.id.etInitialCycles)

        setupSwitchListeners(darkModeSwitch, notificationsSwitch, floatingWindowSwitch, overheatingAlarmSwitch, fahrenheitSwitch)

        // Load and display current cycle count
        scope.launch {
            val currentCycles = withContext(Dispatchers.IO) {
                sharedPreferences.getFloat(CHARGE_CYCLES, 0.0f)
            }
            initialCyclesInput.setText(String.format("%.2f", currentCycles))
        }

        builder.setView(dialogLayout)
        val alertDialog = builder.create()
        alertDialog.show()

        // Implement saving the new value from etInitialCycles to chargeCycles in SharedPreferences
        alertDialog.setOnDismissListener {
            saveInitialCycles(initialCyclesInput)
        }
    }

    private fun setupSwitchListeners(darkModeSwitch: SwitchMaterial, notificationsSwitch: SwitchMaterial, floatingWindowSwitch: SwitchMaterial, overheatingAlarmSwitch: SwitchMaterial, fahrenheitSwitch: SwitchMaterial) {
        darkModeSwitch.isChecked = sharedPreferences.getBoolean(DARK_MODE, false)
        notificationsSwitch.isChecked = sharedPreferences.getBoolean(NOTIFICATIONS, true)
        floatingWindowSwitch.isChecked = sharedPreferences.getBoolean(FLOATING_WINDOW, false)
        overheatingAlarmSwitch.isChecked = sharedPreferences.getBoolean(OVERHEATING_ALARM, true)
        fahrenheitSwitch.isChecked = sharedPreferences.getBoolean(FAHRENHEIT, false)

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
    }

    private fun saveInitialCycles(initialCyclesInput: TextInputEditText) {
        val initialCyclesInputText = initialCyclesInput.text.toString()
        try {
            val initialCycles = initialCyclesInputText.toFloat()
            scope.launch {
                withContext(Dispatchers.IO) {
                    sharedPreferences.edit().putFloat(CHARGE_CYCLES, initialCycles).apply()
                }
                chargeCycles = initialCycles
                updateBatteryInfo(binding.tvLevel.text.toString().replace("Level: ", "").replace("%", "").toInt(), chargeCycles) // Update displayed charge cycles
            }
        } catch (e: NumberFormatException) {
            // Handle invalid input, maybe show an error message
            Log.e("SettingsDialog", "Invalid charge cycles input: ${e.message}")
        }
    }

    private fun initCharts() {
        // Initialize Level Chart
        val levelDataSet = LineDataSet(batteryLevelEntries, "Battery Level").apply {
            color = ContextCompat.getColor(this@MainActivity, R.color.purple_500)
            fillColor = ContextCompat.getColor(this@MainActivity, R.color.purple_200)
            setDrawFilled(true)
        }
        val levelData = LineData(levelDataSet)
        binding.levelChart.apply {
            data = levelData
            description.isEnabled = false
            legend.isEnabled = false
            xAxis.labelRotationAngle = 0f
            invalidate()
        }

        // Initialize Temperature Chart
        val tempDataSet = LineDataSet(temperatureEntries, "Temperature").apply {
            fillColor = ContextCompat.getColor(this@MainActivity, R.color.teal_700)
            lineWidth = 2f // Set line thickness
            color = ContextCompat.getColor(this@MainActivity, R.color.teal_200)
            setDrawFilled(true)
            setDrawCircles(true) // Enable circles
            circleRadius = 3f // Adjust circle radius
            setCircleColor(ContextCompat.getColor(this@MainActivity, android.R.color.white)) // Circle color
            setDrawCircleHole(true) // Enable circle hole
            circleHoleColor = ContextCompat.getColor(this@MainActivity, android.R.color.darker_gray) // Hole color
        }
        val tempData = LineData(tempDataSet)
        binding.tempChart.apply {
            data = tempData

            // X-Axis formatting for time
            xAxis.valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                    return sdf.format(Date(value.toLong()))
                }
            }

            // Y-Axis formatting for temperature (°C)
            axisLeft.valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return "$value°C"
                }
            }
            axisRight.isEnabled = false // Disable right Y-axis
            invalidate()
        }
    }

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

            updateStatusViews(status, health, plugged, voltage, temperature, batteryPct)
            updateChargeCycles(batteryPct)

            previousBatteryPct = batteryPct
            addChartEntry(batteryPct, temperature.toFloat() / 10)
        }
    }

    private fun updateStatusViews(status: Int, health: Int, plugged: Int, voltage: Int, temperature: Int, batteryPct: Float) {
        val statusString = when (status) {
            BatteryManager.BATTERY_STATUS_CHARGING -> getString(R.string.charging)
            BatteryManager.BATTERY_STATUS_DISCHARGING -> getString(R.string.discharging)
            BatteryManager.BATTERY_STATUS_FULL -> getString(R.string.full)
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> getString(R.string.not_charging)
        else -> getString(R.string.unknown)
        }

        val healthString = when (health) {
            BatteryManager.BATTERY_HEALTH_GOOD -> getString(R.string.good)
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> getString(R.string.overheat)
            BatteryManager.BATTERY_HEALTH_DEAD -> getString(R.string.dead)
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> getString(R.string.over_voltage)
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> getString(R.string.unknown)
        else -> getString(R.string.unknown)
        }

        val pluggedString = when (plugged) {
            BatteryManager.BATTERY_PLUGGED_AC -> "AC"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
        else -> "Battery"
        }

        val temperatureC = temperature.toFloat() / 10
        val voltageMV = voltage

        binding.tvStatus.text = getString(R.string.status_format, statusString)
        binding.tvHealth.text = getString(R.string.health_format, healthString)
        binding.tvPower.text = getString(R.string.power_source_format, pluggedString)
        binding.tvVoltage.text = getString(R.string.voltage_format, voltageMV)
        val temperatureFahrenheit = (temperatureC * 9 / 5) + 32
        binding.tvTemperature.text =
            if (sharedPreferences.getBoolean(FAHRENHEIT, false)) getString(R.string.temperature_fahrenheit_format, String.format("%.1f", temperatureFahrenheit)) else getString(R.string.temperature_celsius_format, temperatureC)
        binding.tvLevel.text = getString(R.string.level_format, batteryPct)

        Log.d("Battery", "Level: ${batteryPct}%")
    }

    private fun updateChargeCycles(batteryPct: Float) {
    }

    private fun addChartEntry(batteryPct: Float, temperatureC: Float) {
        val time = System.currentTimeMillis().toFloat()

        batteryLevelEntries.add(Entry(time, batteryPct))
        temperatureEntries.add(Entry(time, temperatureC))

        // Notify data set changed
        (binding.levelChart.data.getDataSetByIndex(0) as LineDataSet).notifyDataSetChanged()
        (binding.tempChart.data.getDataSetByIndex(0) as LineDataSet).notifyDataSetChanged()

        binding.levelChart.data.notifyDataChanged()
        binding.tempChart.data.notifyDataChanged()

        // Refresh chart
        binding.tempChart.invalidate()

        // Limit data points to 150
        if (batteryLevelEntries.size > 150) {
            batteryLevelEntries.removeAt(0)
        }
        if (temperatureEntries.size > 150) {
            temperatureEntries.removeAt(0)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(batteryReceiver)
    }

    private fun updateBatteryInfo(level: Int, cycles: Float) {
        binding.tvLevel.text = getString(R.string.battery_level_format, level)
        binding.chargeCycles.text = String.format(getString(R.string.charge_cycles_format), cycles)
    }

    private fun updateRemainingTime() {
        val batteryManager = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val remainingTime = batteryManager.computeChargeTimeRemaining()

        if (remainingTime > 0) {
            val hours = remainingTime / 3600
            val minutes = (remainingTime % 3600) / 60
            binding.tvRemainingTime.text = getString(R.string.remaining_time_format, hours, minutes)
        } else {
            binding.tvRemainingTime.text = getString(R.string.remaining_time_na)
        }
    }
}