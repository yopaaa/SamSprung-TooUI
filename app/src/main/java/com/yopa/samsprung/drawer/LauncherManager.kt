package com.yopa.samsprung.drawer

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.content.pm.ResolveInfo
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import androidx.appcompat.app.AppCompatActivity
import com.yopa.app.CoverOptions
import com.yopa.content.ScaledContext
import com.yopa.io.Debug
import com.yopa.samsprung.SamSprung
import com.yopa.samsprung.SamSprungOverlay
import com.yopa.samsprung.settings.Preferences

class LauncherManager(private val overlay: SamSprungOverlay) {

    val displayContext = ScaledContext(ScaledContext(overlay).internal(1.5f)).cover()
    val launcher = displayContext.getSystemService(
        AppCompatActivity.LAUNCHER_APPS_SERVICE
    ) as LauncherApps
    val prefs: SharedPreferences = overlay.getSharedPreferences(
        Preferences.prefsValue, AppCompatActivity.MODE_PRIVATE)

    private fun postOrientationHandler(extras: Bundle) {
        val mode = prefs.getString(Preferences.prefAppOrientationMode, null)
        val lockPortrait = when (mode) {
            Preferences.ORIENTATION_MODE_PORTRAIT -> true
            Preferences.ORIENTATION_MODE_AUTO -> false
            else -> prefs.getBoolean(Preferences.prefRotate, false)
        }

        if (lockPortrait) {
            OrientationManager.addOrientationLayout(overlay, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
        } else {
            OrientationManager.removeOrientationLayout(overlay)
        }

        Handler(Looper.getMainLooper()).postDelayed({
            overlay.onStopOverlay()
            val context = ScaledContext(ScaledContext(overlay).internal(1.5f)).cover()
            context.startForegroundService(
                Intent(context, AppDisplayListener::class.java).putExtras(extras)
            )
        }, 20)
    }

    private fun getLaunchBounds() : Rect {
        return overlay.windowManager.maximumWindowMetrics.bounds
    }

    fun launchResolveInfo(resolveInfo: ResolveInfo) {
        overlay.setKeyguardListener(object: SamSprungOverlay.KeyguardListener {
            override fun onKeyguardCheck(unlocked: Boolean) {
                if (!unlocked) return
                overlay.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED
                overlay.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_BEHIND

                val intent = overlay.packageManager
                    .getLaunchIntentForPackage(resolveInfo.activityInfo.packageName)
                val targetComponent = ComponentName(
                    resolveInfo.activityInfo.packageName,
                    resolveInfo.activityInfo.name
                )
                try {
                    launcher.startMainActivity(
                        targetComponent,
                        Process.myUserHandle(),
                        getLaunchBounds(),
                        CoverOptions(null).getAnimatedOptions(
                            1, overlay.getCoordinator(), intent
                        ).toBundle()
                    )
                } catch (_: Exception) {
                    try {
                        val fallbackIntent = (intent ?: Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)).apply {
                            component = targetComponent
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or
                                    Intent.FLAG_ACTIVITY_TASK_ON_HOME or
                                    Intent.FLAG_ACTIVITY_FORWARD_RESULT)
                        }
                        val options = CoverOptions(null).getAnimatedOptions(1, overlay.getCoordinator(), fallbackIntent).toBundle()
                        overlay.startActivity(fallbackIntent, options)
                    } catch (_: Exception) {}
                }

                val extras = Bundle()
                extras.putString("launchPackage", resolveInfo.activityInfo.packageName)
                extras.putString("launchActivity", resolveInfo.activityInfo.name)
                postOrientationHandler(extras)
            }
        })
    }

    fun launchApplicationInfo(appInfo: ApplicationInfo) {
        overlay.setKeyguardListener(object: SamSprungOverlay.KeyguardListener {
            override fun onKeyguardCheck(unlocked: Boolean) {
                if (!unlocked) return
                overlay.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED
                overlay.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_BEHIND

                val intent = overlay.packageManager.getLaunchIntentForPackage(appInfo.packageName)
                try {
                    launcher.startMainActivity(
                        intent?.component,
                        Process.myUserHandle(),
                        getLaunchBounds(),
                        CoverOptions(null).getAnimatedOptions(
                            1, overlay.getCoordinator(), intent
                        ).toBundle()
                    )
                } catch (_: Exception) {
                    try {
                        intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_TASK_ON_HOME or
                                Intent.FLAG_ACTIVITY_FORWARD_RESULT)
                        val options = CoverOptions(null).getAnimatedOptions(1, overlay.getCoordinator(), intent).toBundle()
                        intent?.let { overlay.startActivity(it, options) }
                    } catch (_: Exception) {}
                }

                val extras = Bundle()
                extras.putString("launchPackage", appInfo.packageName)
                postOrientationHandler(extras)
            }
        })
    }

    fun launchPendingIntent(pendingIntent: PendingIntent, packageName: String? = null) {
        overlay.setKeyguardListener(object: SamSprungOverlay.KeyguardListener {
            override fun onKeyguardCheck(unlocked: Boolean) {
                if (!unlocked) return
                overlay.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED
                overlay.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_BEHIND

                val targetPkg = packageName ?: pendingIntent.creatorPackage ?: "android"
                val extras = Bundle().apply {
                    putString("launchPackage", if (targetPkg == "android") "com.android.settings" else targetPkg)
                }

                val options = CoverOptions(null).getActivityOptions(1).toBundle()

                try {
                    pendingIntent.send(
                        displayContext,
                        0,
                        null,
                        null,
                        null,
                        null,
                        options
                    )
                } catch (e: Exception) {
                    android.util.Log.e("LauncherManager", "Failed to send pendingIntent: ${e.message}", e)
                    try {
                        val launchIntent = overlay.packageManager.getLaunchIntentForPackage(targetPkg)
                        if (launchIntent != null) {
                            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_TASK_ON_HOME)
                            displayContext.startActivity(launchIntent, options)
                        }
                    } catch (e2: Exception) {
                        android.util.Log.e("LauncherManager", "Fallback launch failed: ${e2.message}", e2)
                    }
                }

                overlay.closeMainDrawer()
                postOrientationHandler(extras)
            }
        })
    }

    fun launchIntent(intent: Intent, targetPackage: String? = null, targetActivity: String? = null) {
        overlay.setKeyguardListener(object: SamSprungOverlay.KeyguardListener {
            override fun onKeyguardCheck(unlocked: Boolean) {
                if (!unlocked) return
                overlay.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED
                overlay.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_BEHIND

                intent.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_TASK_ON_HOME
                )

                val options = CoverOptions(null).getAnimatedOptions(
                    1, overlay.getCoordinator(), intent
                ).toBundle()

                try {
                    overlay.startActivity(intent, options)
                } catch (e: Exception) {
                    try {
                        displayContext.startActivity(intent, options)
                    } catch (_: Exception) {
                        return
                    }
                }

                val extras = Bundle()
                val pkg = targetPackage ?: intent.component?.packageName ?: intent.`package` ?: "com.android.settings"
                extras.putString("launchPackage", pkg)
                val act = targetActivity ?: intent.component?.className
                act?.let { extras.putString("launchActivity", it) }
                overlay.closeMainDrawer()
                postOrientationHandler(extras)
            }
        })
    }
}