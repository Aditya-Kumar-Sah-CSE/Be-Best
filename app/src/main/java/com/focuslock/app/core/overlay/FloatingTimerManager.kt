package com.focuslock.app.core.overlay

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.focuslock.app.core.util.BreakReminderHelper
import com.focuslock.app.presentation.breakalert.BreakAlertActivity
import java.util.Locale

/**
 * Manages the compact, draggable floating screen-time pill overlay.
 * Displays live continuous screen-on time (e.g. "◷ 24:38 / 30:00")
 * unobtrusively at the top of the screen across all applications (Instagram, YouTube,
 * WhatsApp, Chrome, Games, Home Screen, etc.).
 */
object FloatingTimerManager {

    private const val PREFS_NAME = "focuslock_app_prefs"
    const val KEY_FLOATING_TIMER_ENABLED = "floating_timer_overlay_enabled"
    private const val KEY_OVERLAY_X = "floating_timer_x"
    private const val KEY_OVERLAY_Y = "floating_timer_y"

    @Volatile
    private var isOverlayAttached = false
    private var overlayView: View? = null
    private var timerTextView: TextView? = null
    private var windowManager: WindowManager? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private val tickerRunnable = object : Runnable {
        override fun run() {
            if (isOverlayAttached) {
                updateTimerText()
                mainHandler.postDelayed(this, 1000L)
            }
        }
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun isFloatingTimerEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_FLOATING_TIMER_ENABLED, true)
    }

    fun setFloatingTimerEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_FLOATING_TIMER_ENABLED, enabled).apply()
        if (enabled) {
            FloatingTimerService.start(context)
            show(context)
        } else {
            FloatingTimerService.stop(context)
            hide()
        }
    }

    fun hasOverlayPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    fun requestOverlayPermission(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                try {
                    val fallbackIntent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(fallbackIntent)
                } catch (ignored: Exception) {}
            }
        }
    }

    fun onScreenOn(context: Context) {
        mainHandler.post {
            show(context)
        }
    }

    fun onScreenOff(context: Context) {
        mainHandler.post {
            hide()
        }
    }

    fun refresh(context: Context) {
        mainHandler.post {
            if (isOverlayAttached && overlayView?.isAttachedToWindow == true) {
                updateTimerText()
            } else if (isFloatingTimerEnabled(context) && hasOverlayPermission(context)) {
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                if (powerManager?.isInteractive != false) {
                    show(context)
                }
            }
        }
    }

    fun ensureVisible(context: Context) {
        if (!isFloatingTimerEnabled(context) || !hasOverlayPermission(context)) {
            hide()
            return
        }
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        if (powerManager?.isInteractive == false) {
            hide()
            return
        }

        mainHandler.post {
            if (overlayView == null || !isOverlayAttached || overlayView?.isAttachedToWindow != true) {
                show(context)
            } else {
                updateTimerText()
            }
        }
    }

    @Synchronized
    fun show(context: Context) {
        if (!isFloatingTimerEnabled(context)) return
        if (!hasOverlayPermission(context)) return

        val appContext = context.applicationContext

        mainHandler.post {
            if (isOverlayAttached && overlayView != null && overlayView?.isAttachedToWindow == true) {
                updateTimerText()
                return@post
            }

            try {
                windowManager = appContext.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
                    ?: return@post

                // Clean up any previously detached or stale view
                overlayView?.let { oldView ->
                    try {
                        windowManager?.removeViewImmediate(oldView)
                    } catch (e: Exception) {
                        try {
                            windowManager?.removeView(oldView)
                        } catch (ignored: Exception) {}
                    }
                }

                val pillView = createPillView(appContext)
                overlayView = pillView

                val params = createLayoutParams(appContext)
                layoutParams = params

                attachTouchListener(pillView, params, appContext)

                windowManager?.addView(pillView, params)
                isOverlayAttached = true

                updateTimerText()

                mainHandler.removeCallbacks(tickerRunnable)
                mainHandler.postDelayed(tickerRunnable, 1000L)
            } catch (e: Exception) {
                // Ignore security exceptions on aggressive OEM skins
                isOverlayAttached = false
                overlayView = null
            }
        }
    }

    @Synchronized
    fun hide() {
        mainHandler.removeCallbacks(tickerRunnable)
        mainHandler.post {
            val view = overlayView
            if (isOverlayAttached && view != null) {
                try {
                    windowManager?.removeViewImmediate(view)
                } catch (e: Exception) {
                    try {
                        windowManager?.removeView(view)
                    } catch (ignored: Exception) {}
                }
            }
            isOverlayAttached = false
            overlayView = null
            timerTextView = null
        }
    }

    private fun createLayoutParams(context: Context): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        // FLAG_NOT_FOCUSABLE ensures touches outside the pill pass through directly to background apps
        val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL

        val prefs = getPrefs(context)
        val displayMetrics = context.resources.displayMetrics
        val statusBarHeight = getStatusBarHeight(context)

        val pillWidthEstimate = dpToPx(context, 148)
        val defaultX = ((displayMetrics.widthPixels - pillWidthEstimate) / 2).coerceAtLeast(16)
        val defaultY = statusBarHeight + dpToPx(context, 10)

        val savedX = prefs.getInt(KEY_OVERLAY_X, defaultX)
        val savedY = prefs.getInt(KEY_OVERLAY_Y, defaultY)

        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            flags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = savedX.coerceIn(0, displayMetrics.widthPixels - dpToPx(context, 80))
            y = savedY.coerceIn(statusBarHeight, displayMetrics.heightPixels - dpToPx(context, 60))
        }
    }

    private fun createPillView(context: Context): View {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(
                dpToPx(context, 12),
                dpToPx(context, 6),
                dpToPx(context, 12),
                dpToPx(context, 6)
            )

            // Modern translucent dark pill background with subtle cyan border
            val backgroundDrawable = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(context, 20).toFloat()
                setColor(Color.parseColor("#E60B132B")) // 90% opacity deep navy
                setStroke(dpToPx(context, 1), Color.parseColor("#4D38BDF8")) // subtle cyan border
            }
            background = backgroundDrawable
            elevation = dpToPx(context, 6).toFloat()
        }

        val tv = TextView(context).apply {
            text = "◷ 00:00 / 30:00"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            typeface = Typeface.MONOSPACE
            paint.isFakeBoldText = true
            includeFontPadding = false
        }
        timerTextView = tv

        root.addView(tv)
        return root
    }

    private fun attachTouchListener(view: View, params: WindowManager.LayoutParams, context: Context) {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false
        val touchSlop = dpToPx(context, 8)

        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()

                    if (Math.hypot(dx.toDouble(), dy.toDouble()) > touchSlop) {
                        isDragging = true
                    }

                    if (isDragging) {
                        val displayMetrics = context.resources.displayMetrics
                        val statusBarHeight = getStatusBarHeight(context)
                        val viewWidth = v.width.takeIf { it > 0 } ?: dpToPx(context, 140)
                        val viewHeight = v.height.takeIf { it > 0 } ?: dpToPx(context, 32)

                        params.x = (initialX + dx).coerceIn(0, displayMetrics.widthPixels - viewWidth)
                        params.y = (initialY + dy).coerceIn(statusBarHeight, displayMetrics.heightPixels - viewHeight - dpToPx(context, 40))

                        try {
                            windowManager?.updateViewLayout(v, params)
                        } catch (ignored: Exception) {}
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        // User tapped the floating pill: launch 30-minute break alert screen
                        try {
                            BreakAlertActivity.launch(context)
                        } catch (e: Exception) {}
                    } else {
                        // Save last user-preferred position
                        getPrefs(context).edit()
                            .putInt(KEY_OVERLAY_X, params.x)
                            .putInt(KEY_OVERLAY_Y, params.y)
                            .apply()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun updateTimerText() {
        val tv = timerTextView ?: return
        val context = tv.context ?: return

        val continuousMillis = BreakReminderHelper.getContinuousScreenUsageMillis(context)
        val elapsedSecs = (continuousMillis / 1000L).coerceAtLeast(0L)
        val mm = elapsedSecs / 60
        val ss = elapsedSecs % 60

        val formatted = String.format(Locale.US, "◷ %02d:%02d / 30:00", mm, ss)
        tv.text = formatted

        // If continuous usage exceeds 30 minutes, highlight pill in warning red/amber
        if (elapsedSecs >= 1800L) {
            tv.setTextColor(Color.parseColor("#F87171")) // Warning soft red
            (overlayView?.background as? GradientDrawable)?.setStroke(
                dpToPx(context, 1.5f.toInt()),
                Color.parseColor("#EF4444")
            )
        } else if (elapsedSecs >= 1440L) { // 24 minutes (80%)
            tv.setTextColor(Color.parseColor("#FBBF24")) // Amber
            (overlayView?.background as? GradientDrawable)?.setStroke(
                dpToPx(context, 1),
                Color.parseColor("#F59E0B")
            )
        } else {
            tv.setTextColor(Color.WHITE)
            (overlayView?.background as? GradientDrawable)?.setStroke(
                dpToPx(context, 1),
                Color.parseColor("#4D38BDF8") // Subtle cyan
            )
        }
    }

    private fun getStatusBarHeight(context: Context): Int {
        var result = dpToPx(context, 24)
        val resourceId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        if (resourceId > 0) {
            result = context.resources.getDimensionPixelSize(resourceId)
        }
        return result
    }

    private fun dpToPx(context: Context, dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }
}
