package uz.smsbridge.pro.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import uz.smsbridge.pro.R
import uz.smsbridge.pro.SmsBridgeApplication
import uz.smsbridge.pro.data.model.SmsLogEntry
import uz.smsbridge.pro.data.preferences.AppPreferences
import uz.smsbridge.pro.databinding.ActivityMainBinding
import uz.smsbridge.pro.network.CrmApiClient
import uz.smsbridge.pro.service.SmsPollingService
import uz.smsbridge.pro.util.SmsBridgeEvents

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val preferences by lazy { SmsBridgeApplication.preferences }
    private val apiClient = CrmApiClient()

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            val smsGranted = results[Manifest.permission.SEND_SMS] == true
            if (smsGranted) {
                Toast.makeText(this, "SMS ruxsati berildi", Toast.LENGTH_SHORT).show()
                startServiceIfConfigured()
            } else {
                Toast.makeText(
                    this,
                    "SMS yuborish ruxsatisiz xizmat ishlay olmaydi",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupInitialData()
        setupListeners()
        observeEvents()
        checkBatteryOptimization()
    }

    override fun onResume() {
        super.onResume()
        updateStatsUi()
        renderLogs()
        checkBatteryOptimization()
    }

    private fun setupInitialData() {
        binding.etServerUrl.setText(preferences.serverUrl)

        when (preferences.selectedSimSlot) {
            AppPreferences.SIM_2 -> binding.rbSim2.isChecked = true
            AppPreferences.SIM_AUTO -> binding.rbSimAuto.isChecked = true
            else -> binding.rbSim1.isChecked = true
        }

        updateServiceButtonUi(preferences.isServiceRunning)
        updateStatsUi()
        renderLogs()
    }

    private fun setupListeners() {
        // Server URL changes
        binding.etServerUrl.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                preferences.serverUrl = s.toString().trim()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Ping check button
        binding.btnCheckConnection.setOnClickListener {
            performPing()
        }

        // SIM slot selection
        binding.rgSimSlot.setOnCheckedChangeListener { _, checkedId ->
            val slot = when (checkedId) {
                R.id.rbSim2 -> AppPreferences.SIM_2
                R.id.rbSimAuto -> AppPreferences.SIM_AUTO
                else -> AppPreferences.SIM_1
            }
            preferences.selectedSimSlot = slot
        }

        // Start / Stop service button
        binding.btnToggleService.setOnClickListener {
            if (preferences.isServiceRunning) {
                stopSmsService()
            } else {
                checkPermissionsAndStart()
            }
        }

        // Clear logs button
        binding.btnClearLogs.setOnClickListener {
            preferences.clearLogs()
            renderLogs()
            Toast.makeText(this, "Jurnal tozalandi", Toast.LENGTH_SHORT).show()
        }

        // Battery optimization exemption request button
        binding.btnDisableBatteryOpt.setOnClickListener {
            requestBatteryOptimizationExemption()
        }
    }

    private fun observeEvents() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    SmsBridgeEvents.isServiceRunning.collect { running ->
                        updateServiceButtonUi(running)
                    }
                }
                launch {
                    SmsBridgeEvents.isConnected.collect { connected ->
                        updateConnectionBadge(connected)
                    }
                }
                launch {
                    SmsBridgeEvents.statsUpdated.collect {
                        updateStatsUi()
                    }
                }
                launch {
                    SmsBridgeEvents.newLog.collect {
                        renderLogs()
                    }
                }
            }
        }
    }

    private fun performPing() {
        val rawUrl = binding.etServerUrl.text.toString().trim()
        if (rawUrl.isBlank()) {
            binding.etServerUrl.error = getString(R.string.url_empty_error)
            return
        }

        preferences.serverUrl = rawUrl

        // Set UI to checking state
        binding.layoutConnectionBadge.setBackgroundResource(R.drawable.bg_badge_checking)
        binding.tvStatusDot.setTextColor(ContextCompat.getColor(this, R.color.status_warning))
        binding.tvConnectionStatus.text = getString(R.string.status_checking)
        binding.tvPingResult.text = getString(R.string.status_checking)
        binding.btnCheckConnection.isEnabled = false

        lifecycleScope.launch {
            val result = apiClient.ping(rawUrl)
            binding.btnCheckConnection.isEnabled = true

            if (result.isSuccess) {
                updateConnectionBadge(true)
                binding.tvPingResult.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.emerald_400))
                binding.tvPingResult.text = getString(R.string.ping_success, result.latencyMs)
            } else {
                updateConnectionBadge(false)
                binding.tvPingResult.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.danger_red))
                binding.tvPingResult.text = result.message
            }
        }
    }

    private fun updateConnectionBadge(isConnected: Boolean) {
        if (isConnected) {
            binding.layoutConnectionBadge.setBackgroundResource(R.drawable.bg_badge_connected)
            binding.tvStatusDot.setTextColor(ContextCompat.getColor(this, R.color.status_connected))
            binding.tvConnectionStatus.text = getString(R.string.status_connected)
        } else {
            binding.layoutConnectionBadge.setBackgroundResource(R.drawable.bg_badge_disconnected)
            binding.tvStatusDot.setTextColor(ContextCompat.getColor(this, R.color.status_disconnected))
            binding.tvConnectionStatus.text = getString(R.string.status_disconnected)
        }
    }

    private fun updateServiceButtonUi(isRunning: Boolean) {
        if (isRunning) {
            binding.btnToggleService.setBackgroundResource(R.drawable.btn_danger_glow)
            binding.btnToggleService.text = getString(R.string.btn_stop_service)
            binding.btnToggleService.setTextColor(ContextCompat.getColor(this, R.color.white))
            binding.tvServiceStatusDesc.text = getString(R.string.service_running_desc)
            binding.tvServiceStatusDesc.setTextColor(ContextCompat.getColor(this, R.color.emerald_400))
        } else {
            binding.btnToggleService.setBackgroundResource(R.drawable.btn_emerald_glow)
            binding.btnToggleService.text = getString(R.string.btn_start_service)
            binding.btnToggleService.setTextColor(ContextCompat.getColor(this, R.color.bg_dark))
            binding.tvServiceStatusDesc.text = getString(R.string.service_stopped_desc)
            binding.tvServiceStatusDesc.setTextColor(ContextCompat.getColor(this, R.color.text_muted))
        }
    }

    private fun checkPermissionsAndStart() {
        val permissions = mutableListOf(
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_PHONE_STATE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        } else {
            startServiceIfConfigured()
        }
    }

    private fun startServiceIfConfigured() {
        val url = binding.etServerUrl.text.toString().trim()
        if (url.isBlank()) {
            binding.etServerUrl.error = getString(R.string.url_empty_error)
            Toast.makeText(this, getString(R.string.url_empty_error), Toast.LENGTH_SHORT).show()
            return
        }

        preferences.serverUrl = url
        SmsPollingService.start(this)
        Toast.makeText(this, "SMS Bridge xizmati ishga tushirildi", Toast.LENGTH_SHORT).show()
    }

    private fun stopSmsService() {
        SmsPollingService.stop(this)
        Toast.makeText(this, "SMS Bridge xizmati to'xtatildi", Toast.LENGTH_SHORT).show()
    }

    private fun updateStatsUi() {
        binding.tvTotalSentCount.text = preferences.totalSentCount.toString()
        binding.tvSuccessCount.text = preferences.successCount.toString()
        binding.tvFailedCount.text = preferences.failedCount.toString()
    }

    private fun renderLogs() {
        val logs = preferences.getRecentLogs()
        if (logs.isEmpty()) {
            binding.tvConsoleLogs.text = getString(R.string.empty_console_logs)
            binding.tvConsoleLogs.setTextColor(ContextCompat.getColor(this, R.color.text_muted))
        } else {
            val sb = StringBuilder()
            logs.take(15).forEach { entry ->
                sb.append(entry.toFormattedString()).append("\n")
            }
            binding.tvConsoleLogs.text = sb.toString().trimEnd()
            binding.tvConsoleLogs.setTextColor(ContextCompat.getColor(this, R.color.text_console))
        }
    }

    private fun checkBatteryOptimization() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && powerManager != null) {
            val isIgnored = powerManager.isIgnoringBatteryOptimizations(packageName)
            binding.cardBatteryWarning.visibility = if (isIgnored) View.GONE else View.VISIBLE
        } else {
            binding.cardBatteryWarning.visibility = View.GONE
        }
    }

    @SuppressLint("BatteryLife")
    private fun requestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } catch (e: Exception) {
                val fallbackIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                startActivity(fallbackIntent)
            }
        }
    }
}
