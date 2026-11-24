package com.focusguard.app

import android.accessibilityservice.AccessibilityService
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Build
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.google.android.material.button.MaterialButton

class FocusGuardAccessibilityService : AccessibilityService() {

    private lateinit var settings: FocusGuardSettings
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var isOverlayShowing = false
    private var countDownTimer: CountDownTimer? = null

    private var isGuardPaused = false
    private var lastInspectionTime = 0L
    private val mainHandler = Handler(Looper.getMainLooper())
    private val notificationManager by lazy { getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager }

    private val controlReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_STOP_GUARD -> pauseGuard()
                ACTION_RESUME_GUARD -> resumeGuard()
            }
        }
    }

    companion object {
        private const val TAG = "FocusGuard"
        private const val NOTIFICATION_ID = 3001
        private const val CHANNEL_ID = "focus_guard_channel"

        const val ACTION_STOP_GUARD = "com.focusguard.app.ACTION_STOP_GUARD"
        const val ACTION_RESUME_GUARD = "com.focusguard.app.ACTION_RESUME_GUARD"

        var isServiceConnected = false
            private set
    }

    override fun onCreate() {
        super.onCreate()
        settings = FocusGuardSettings(this)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val filter = IntentFilter().apply {
            addAction(ACTION_STOP_GUARD)
            addAction(ACTION_RESUME_GUARD)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(controlReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(controlReceiver, filter)
        }

        createNotificationChannel()
        if (settings.isMasterGuardEnabled) {
            showStatusNotification()
        }
        Log.d(TAG, "FocusGuardAccessibilityService created")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        isServiceConnected = true
        if (!settings.isMasterGuardEnabled) {
            isGuardPaused = true
            Log.d(TAG, "FocusGuard accessibility connected but Master Guard is OFF. Staying dormant.")
            return
        }
        isGuardPaused = false
        showStatusNotification()
        Log.d(TAG, "FocusGuard accessibility service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || isGuardPaused || !settings.isMasterGuardEnabled) return

        val pkgName = event.packageName?.toString() ?: return

        // 1. Check if monitored platform is enabled in settings
        val isTarget = when (pkgName) {
            "com.instagram.android" -> settings.isInstagramEnabled
            "com.google.android.youtube" -> settings.isYouTubeEnabled
            else -> false
        }
        if (!isTarget) return

        // 2. Check if user already has an active 15-minute pass
        if (settings.hasActivePass() || isOverlayShowing) return

        val className = event.className?.toString() ?: ""
        val now = System.currentTimeMillis()

        // 3. Fast Activity/Fragment check on Window State Changes (Instant & Zero tree traversal)
        val isFastMatch = when (pkgName) {
            "com.instagram.android" -> className.contains("ReelsViewerActivity", ignoreCase = true) ||
                    className.contains("ClipsViewer", ignoreCase = true)
            "com.google.android.youtube" -> className.contains("ShortsActivity", ignoreCase = true) ||
                    className.contains("ReelWatch", ignoreCase = true)
            else -> false
        }

        if (isFastMatch) {
            Log.i(TAG, "Fast doomscroll signature matched via activity class: $className")
            mainHandler.post { showFrictionOverlay() }
            return
        }

        // 4. Rate-limit recursive view hierarchy traversal to at most once per 1200ms
        if (now - lastInspectionTime < 1200L && event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            return
        }
        lastInspectionTime = now

        val rootNode = rootInActiveWindow ?: return
        val isReelsOrShorts = detectReelsOrShorts(rootNode, pkgName)

        if (isReelsOrShorts) {
            Log.i(TAG, "Doomscroll trigger confirmed in $pkgName! Launching Mindful Pause overlay...")
            mainHandler.post { showFrictionOverlay() }
        }
    }

    private fun detectReelsOrShorts(node: AccessibilityNodeInfo?, pkg: String): Boolean {
        if (node == null) return false

        val viewId = node.viewIdResourceName?.lowercase() ?: ""
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""
        val text = node.text?.toString()?.lowercase() ?: ""

        if (pkg == "com.instagram.android") {
            if (viewId.contains("clips") || viewId.contains("reel") ||
                desc.contains("reels") || desc.contains("reel")) {
                return true
            }
        } else if (pkg == "com.google.android.youtube") {
            if (viewId.contains("reel") || viewId.contains("shorts") ||
                desc.contains("shorts") || text.contains("shorts")) {
                return true
            }
        }

        for (i in 0 until minOf(node.childCount, 12)) {
            val child = node.getChild(i)
            if (detectReelsOrShorts(child, pkg)) return true
        }

        return false
    }

    private fun showFrictionOverlay() {
        if (isOverlayShowing || !Settings.canDrawOverlays(this)) return

        try {
            isOverlayShowing = true
            val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.CENTER
            }

            val inflater = LayoutInflater.from(this)
            overlayView = inflater.inflate(R.layout.overlay_friction_dialog, null)

            val tvCountdown = overlayView?.findViewById<TextView>(R.id.tv_countdown_seconds)
            val btnLeave = overlayView?.findViewById<MaterialButton>(R.id.btn_leave_reels)
            val btnGrantPass = overlayView?.findViewById<MaterialButton>(R.id.btn_grant_pass)

            val totalSeconds = settings.frictionSeconds

            btnLeave?.setOnClickListener {
                settings.recordBlockedSession()
                dismissOverlay()
                // Send Back to leave the Reels fragment immediately
                performGlobalAction(GLOBAL_ACTION_BACK)
                mainHandler.postDelayed({ performGlobalAction(GLOBAL_ACTION_BACK) }, 300L)
                Toast.makeText(this, "Great focus! Returned to feed.", Toast.LENGTH_SHORT).show()
            }

            btnGrantPass?.setOnClickListener {
                settings.grantPass(15)
                dismissOverlay()
                Toast.makeText(this, "15-minute pass active. Scroll mindfully!", Toast.LENGTH_SHORT).show()
            }

            // Start countdown timer
            countDownTimer?.cancel()
            countDownTimer = object : CountDownTimer(totalSeconds * 1000L, 1000L) {
                override fun onTick(millisUntilFinished: Long) {
                    val secLeft = (millisUntilFinished / 1000L) + 1
                    tvCountdown?.text = secLeft.toString()
                    btnGrantPass?.text = "Allow 15 Minutes Pass (Wait ${secLeft}s)"
                }

                override fun onFinish() {
                    tvCountdown?.text = "0"
                    btnGrantPass?.isEnabled = true
                    btnGrantPass?.text = "Allow 15 Minutes Pass"
                }
            }.start()

            windowManager?.addView(overlayView, params)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show friction overlay: ${e.message}", e)
            isOverlayShowing = false
        }
    }

    private fun dismissOverlay() {
        countDownTimer?.cancel()
        countDownTimer = null
        if (overlayView != null && isOverlayShowing) {
            try {
                windowManager?.removeView(overlayView)
            } catch (_: Exception) {}
            overlayView = null
        }
        isOverlayShowing = false
    }

    private fun pauseGuard() {
        settings.isMasterGuardEnabled = false
        isGuardPaused = true
        dismissOverlay()
        showStatusNotification()
        Toast.makeText(this, "Smart Focus Guard Stopped", Toast.LENGTH_SHORT).show()
        Log.d(TAG, "Guard paused via notification action")
    }

    private fun resumeGuard() {
        settings.isMasterGuardEnabled = true
        isGuardPaused = false
        showStatusNotification()
        Toast.makeText(this, "Smart Focus Guard Resumed", Toast.LENGTH_SHORT).show()
        Log.d(TAG, "Guard resumed via notification action")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Focus Guard Controls",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Status notification with quick Stop and Resume controls"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun showStatusNotification() {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPending = PendingIntent.getActivity(
            this, 0, openAppIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(openPending)
            .setOngoing(!isGuardPaused)

        if (isGuardPaused) {
            val resumeIntent = Intent(ACTION_RESUME_GUARD).apply { setPackage(packageName) }
            val resumePending = PendingIntent.getBroadcast(
                this, 2, resumeIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.setContentTitle("Focus Guard: Stopped / Closed")
                .setContentText("Doomscroll blocker is off. Tap Resume to restart.")
                .addAction(android.R.drawable.ic_media_play, "▶ RESUME", resumePending)
        } else {
            val stopIntent = Intent(ACTION_STOP_GUARD).apply { setPackage(packageName) }
            val stopPending = PendingIntent.getBroadcast(
                this, 1, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.setContentTitle("Focus Guard: Active")
                .setContentText("Protecting Reels & Shorts. Tap Stop to shut off.")
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "🛑 STOP GUARD", stopPending)
        }

        notificationManager.notify(NOTIFICATION_ID, builder.build())
    }

    override fun onInterrupt() {
        dismissOverlay()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(controlReceiver)
        } catch (_: Exception) {}
        notificationManager.cancel(NOTIFICATION_ID)
        dismissOverlay()
        isServiceConnected = false
    }
}
