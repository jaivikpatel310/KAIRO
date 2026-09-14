package com.example.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager

/**
 * Manages the floating pulsing orb system overlay.
 * Uses TYPE_APPLICATION_OVERLAY on Android O+.
 * Voice-only: passes through touches with FLAG_NOT_TOUCHABLE.
 */
class OrbOverlayManager(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private var orbView: OrbView? = null
    private var isAttached = false

    fun show(initialStatus: String = "Listening...", secondary: String = "KAIRO") {
        mainHandler.post {
            if (!canDrawOverlay()) {
                return@post
            }

            if (orbView == null) {
                orbView = OrbView(context)
            }

            orbView?.setStatus(initialStatus, secondary)

            if (!isAttached && windowManager != null) {
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
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.CENTER
                }

                try {
                    windowManager.addView(orbView, params)
                    isAttached = true
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun updateStatus(status: String, secondary: String = "KAIRO") {
        mainHandler.post {
            orbView?.setStatus(status, secondary)
        }
    }

    fun updateRms(rmsDb: Float) {
        mainHandler.post {
            orbView?.setRms(rmsDb)
        }
    }

    fun hide() {
        mainHandler.post {
            if (isAttached && orbView != null && windowManager != null) {
                try {
                    windowManager.removeView(orbView)
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    isAttached = false
                }
            }
        }
    }

    fun isShowing(): Boolean = isAttached

    private fun canDrawOverlay(): Boolean {
        return Settings.canDrawOverlays(context)
    }
}
