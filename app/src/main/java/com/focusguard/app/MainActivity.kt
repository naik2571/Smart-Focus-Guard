package com.focusguard.app

import android.app.AlertDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.CountDownTimer
import android.provider.Settings
import android.text.TextUtils
import android.view.LayoutInflater
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.focusguard.app.databinding.ActivityMainBinding
import com.google.android.material.button.MaterialButton

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var settings: FocusGuardSettings

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        settings = FocusGuardSettings(this)

        setupSwitches()
        setupTimerChips()
        setupButtons()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionButtons()
        updateStats()
    }

    private fun setupSwitches() {
        binding.switchMasterGuard.isChecked = settings.isMasterGuardEnabled
        updateMasterGuardUI(settings.isMasterGuardEnabled)

        binding.switchMasterGuard.setOnCheckedChangeListener { _, isChecked ->
            settings.isMasterGuardEnabled = isChecked
            updateMasterGuardUI(isChecked)
            if (isChecked) {
                val resumeIntent = Intent(FocusGuardAccessibilityService.ACTION_RESUME_GUARD).apply {
                    setPackage(packageName)
                }
                sendBroadcast(resumeIntent)
                Toast.makeText(this, "Focus Guard Protection Activated", Toast.LENGTH_SHORT).show()
            } else {
                val stopIntent = Intent(FocusGuardAccessibilityService.ACTION_STOP_GUARD).apply {
                    setPackage(packageName)
                }
                sendBroadcast(stopIntent)
                Toast.makeText(this, "Focus Guard Deactivated (Dormant)", Toast.LENGTH_SHORT).show()
            }
        }

        binding.switchInstagram.isChecked = settings.isInstagramEnabled
        binding.switchInstagram.setOnCheckedChangeListener { _, isChecked ->
            settings.isInstagramEnabled = isChecked
        }

        binding.switchYoutube.isChecked = settings.isYouTubeEnabled
        binding.switchYoutube.setOnCheckedChangeListener { _, isChecked ->
            settings.isYouTubeEnabled = isChecked
        }
    }

    private fun updateMasterGuardUI(enabled: Boolean) {
        if (enabled) {
            binding.tvGuardMasterStatus.text = "Status: ACTIVE (Shield on)"
            binding.tvGuardMasterStatus.setTextColor(Color.parseColor("#10B981"))
        } else {
            binding.tvGuardMasterStatus.text = "Status: OFF (Dormant)"
            binding.tvGuardMasterStatus.setTextColor(Color.parseColor("#94A3B8"))
        }
    }

    private fun setupTimerChips() {
        when (settings.frictionSeconds) {
            5 -> binding.chip5s.isChecked = true
            15 -> binding.chip15s.isChecked = true
            else -> binding.chip10s.isChecked = true
        }

        binding.chipGroupTimer.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isEmpty()) return@setOnCheckedStateChangeListener
            val seconds = when (checkedIds.first()) {
                R.id.chip_5s -> 5
                R.id.chip_15s -> 15
                else -> 10
            }
            settings.frictionSeconds = seconds
            Toast.makeText(this, "Countdown set to ${seconds}s", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupButtons() {
        binding.btnAccessibilityStatus.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
            Toast.makeText(this, "Enable 'Smart Focus Guard' in Accessibility", Toast.LENGTH_LONG).show()
        }

        binding.btnOverlayStatus.setOnClickListener {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
            Toast.makeText(this, "Allow 'Display over other apps'", Toast.LENGTH_LONG).show()
        }

        binding.btnPreviewOverlay.setOnClickListener {
            showPreviewDialog()
        }
    }

    private fun showPreviewDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.overlay_friction_dialog, null)
        val tvCountdown = dialogView.findViewById<TextView>(R.id.tv_countdown_seconds)
        val btnLeave = dialogView.findViewById<MaterialButton>(R.id.btn_leave_reels)
        val btnGrant = dialogView.findViewById<MaterialButton>(R.id.btn_grant_pass)

        val dialog = AlertDialog.Builder(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
            .setView(dialogView)
            .create()

        val totalSeconds = settings.frictionSeconds

        val timer = object : CountDownTimer(totalSeconds * 1000L, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                val secLeft = (millisUntilFinished / 1000L) + 1
                tvCountdown.text = secLeft.toString()
                btnGrant.text = "Allow 15 Minutes Pass (Wait ${secLeft}s)"
            }

            override fun onFinish() {
                tvCountdown.text = "0"
                btnGrant.isEnabled = true
                btnGrant.text = "Allow 15 Minutes Pass"
            }
        }.start()

        btnLeave.setOnClickListener {
            timer.cancel()
            dialog.dismiss()
            settings.recordBlockedSession()
            updateStats()
            Toast.makeText(this, "Mindful choice! Session saved.", Toast.LENGTH_SHORT).show()
        }

        btnGrant.setOnClickListener {
            timer.cancel()
            dialog.dismiss()
            settings.grantPass(15)
            Toast.makeText(this, "15-minute pass activated for testing!", Toast.LENGTH_SHORT).show()
        }

        dialog.show()
    }

    private fun updateStats() {
        val blocked = settings.sessionsBlockedToday
        val minutesSaved = blocked * 12
        binding.tvStatsBlocked.text = blocked.toString()
        binding.tvStatsMinutes.text = "~${minutesSaved}m"
    }

    private fun updatePermissionButtons() {
        // 1. Accessibility Service Check
        val isAccessibilityEnabled = isAccessibilityServiceEnabled(this, FocusGuardAccessibilityService::class.java)
        if (isAccessibilityEnabled) {
            binding.btnAccessibilityStatus.text = "Active ✓"
            binding.btnAccessibilityStatus.setTextColor(Color.parseColor("#10B981"))
            binding.btnAccessibilityStatus.isEnabled = false
        } else {
            binding.btnAccessibilityStatus.text = "Enable"
            binding.btnAccessibilityStatus.setTextColor(Color.parseColor("#F59E0B"))
            binding.btnAccessibilityStatus.isEnabled = true
        }

        // 2. Overlay Permission Check
        val canDrawOverlays = Settings.canDrawOverlays(this)
        if (canDrawOverlays) {
            binding.btnOverlayStatus.text = "Granted ✓"
            binding.btnOverlayStatus.setTextColor(Color.parseColor("#10B981"))
            binding.btnOverlayStatus.isEnabled = false
        } else {
            binding.btnOverlayStatus.text = "Grant"
            binding.btnOverlayStatus.setTextColor(Color.parseColor("#38BDF8"))
            binding.btnOverlayStatus.isEnabled = true
        }
    }

    private fun isAccessibilityServiceEnabled(context: Context, service: Class<*>): Boolean {
        val expectedComponentName = "${context.packageName}/${service.name}"
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServices)
        while (colonSplitter.hasNext()) {
            val componentName = colonSplitter.next()
            if (componentName.equals(expectedComponentName, ignoreCase = true)) {
                return true
            }
        }
        return false
    }
}
