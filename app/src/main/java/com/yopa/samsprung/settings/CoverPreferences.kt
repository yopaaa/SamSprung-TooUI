/*
 * ====================================================================
 * Copyright (c) 2021-2023 AbandonedCart.  All rights reserved.
 *
 * See https://github.com/SamSprung/.github/blob/main/LICENSE#L5
 * ====================================================================
 *
 * The license and distribution terms for any publicly available version or
 * derivative of this code cannot be changed.  i.e. this code cannot simply be
 * copied and put under another distribution license
 * [including the GNU Public License.] Content not subject to these terms is
 * subject to to the terms and conditions of the Apache License, Version 2.0.
 */

package com.yopa.samsprung.settings

import android.Manifest
import android.annotation.SuppressLint
import android.app.AppOpsManager
import android.app.KeyguardManager
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.os.Process
import android.provider.MediaStore
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import android.webkit.MimeTypeMap
import android.widget.Switch
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.color.MaterialColors
import com.google.android.material.navigation.NavigationView
import com.yopa.content.ScaledContext
import com.yopa.io.Debug
import com.yopa.os.Version
import com.yopa.samsprung.*
import com.yopa.samsprung.settings.screens.*
import com.yopa.samsprung.update.UpdateManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

@SuppressLint("UseSwitchCompatOrMaterialCode")
class CoverPreferences : AppCompatActivity() {

    private val prefs: SharedPreferences by lazy {
        getSharedPreferences(Preferences.prefsValue, MODE_PRIVATE)
    }

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private var mainSwitch: Switch? = null
    private val donationManager = DonationManager(this)
    private var updateManager: UpdateManager? = null

    // Activity Result Launchers
    private val requestNotification = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    private val requestVoice = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        val currentFrag = supportFragmentManager.findFragmentById(R.id.container_settings)
        if (currentFrag is PermissionsScreenFragment) {
            currentFrag.updatePermissionStates()
        }
    }

    private val overlayLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        mainSwitch?.isChecked = Settings.canDrawOverlays(applicationContext)
        val currentFrag = supportFragmentManager.findFragmentById(R.id.container_settings)
        if (currentFrag is PermissionsScreenFragment) {
            currentFrag.updatePermissionStates()
        }
        if (Version.isTiramisu && mainSwitch?.isChecked == true) {
            requestNotification.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        try {
            ScaledContext(this).cover().run {
                startForegroundService(Intent(this, OnBroadcastService::class.java))
            }
        } catch (_: IllegalArgumentException) {
            Toast.makeText(
                this@CoverPreferences, R.string.display_unavailable, Toast.LENGTH_SHORT
            ).show()
        }
    }

    val updateLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (packageManager.canRequestPackageInstalls()) {
            updateManager?.requestUpdateJSON()
        }
    }

    private val onPickImage = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { document: Uri? ->
        document?.let {
            contentResolver.takePersistableUriPermission(document, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            contentResolver.takePersistableUriPermission(document, Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            val extension: String? = when {
                document.scheme == ContentResolver.SCHEME_CONTENT -> {
                    MimeTypeMap.getSingleton().getExtensionFromMimeType(
                        contentResolver.getType(document)
                    )
                }
                null != document.path -> {
                    MimeTypeMap.getFileExtensionFromUrl(
                        Uri.fromFile(File(document.path!!)).toString()
                    )
                }
                else -> null
            }
            if (extension.equals("gif", true)) {
                saveAnimatedImage(document)
            } else {
                saveStaticImage(document)
            }
            Toast.makeText(this@CoverPreferences, "Wallpaper cover screen berhasil diperbarui", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setTheme(R.style.Theme_SecondScreen_NoActionBar)
        setContentView(R.layout.preferences_layout)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_menu_24)

        toolbar.setNavigationOnClickListener {
            if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                drawerLayout.closeDrawer(GravityCompat.START)
            } else {
                drawerLayout.openDrawer(GravityCompat.START)
            }
        }

        // Rebind notification listener component if needed
        val componentName = ComponentName(applicationContext, NotificationReceiver::class.java)
        packageManager.setComponentEnabledSetting(componentName,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP
        )
        packageManager.setComponentEnabledSetting(componentName,
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP
        )

        donationManager.retrieveDonationMenu()

        drawerLayout = findViewById(R.id.drawer_layout)
        navigationView = findViewById(R.id.navigation_view)

        navigationView.setNavigationItemSelectedListener { item ->
            showScreen(item.itemId)
            drawerLayout.closeDrawer(GravityCompat.START)
            true
        }

        if (savedInstanceState == null) {
            showScreen(R.id.nav_permissions)
            navigationView.setCheckedItem(R.id.nav_permissions)
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawer(GravityCompat.START)
                } else {
                    finish()
                }
            }
        })
    }

    private fun showScreen(itemId: Int) {
        val (fragment, title) = when (itemId) {
            R.id.nav_permissions -> Pair(PermissionsScreenFragment(), "Izin & Aksesibilitas")
            R.id.nav_cover_screen -> Pair(CoverScreenFragment(), "Tampilan Layar Cover")
            R.id.nav_apps_manager -> Pair(AppsManagerScreenFragment(), "Laci & Visibilitas Aplikasi")
            R.id.nav_gestures -> Pair(GesturesScreenFragment(), "Gestur & Navigasi")
            R.id.nav_about -> Pair(AboutScreenFragment(), "Tentang & Bantuan")
            else -> return
        }

        supportActionBar?.title = title
        supportFragmentManager.beginTransaction()
            .replace(R.id.container_settings, fragment)
            .commit()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.cover_settings_menu, menu)
        val actionSwitch: MenuItem = menu.findItem(R.id.switch_action_bar)
        actionSwitch.setActionView(R.layout.configure_switch)
        mainSwitch = actionSwitch.actionView?.findViewById(R.id.switch2)
        mainSwitch?.isChecked = Settings.canDrawOverlays(applicationContext)
        if (Version.isTiramisu && mainSwitch?.isChecked == true) {
            requestNotification.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        mainSwitch?.setOnClickListener {
            overlayLauncher.launch(Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            ))
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                drawerLayout.closeDrawer(GravityCompat.START)
            } else {
                drawerLayout.openDrawer(GravityCompat.START)
            }
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onStart() {
        super.onStart()
        try {
            ScaledContext(this).cover().run {
                startForegroundService(Intent(this, OnBroadcastService::class.java))
            }
        } catch (_: IllegalArgumentException) {
            Toast.makeText(
                this@CoverPreferences, R.string.display_unavailable, Toast.LENGTH_SHORT
            ).show()
        }

        updateManager = UpdateManager(this@CoverPreferences)
    }

    // --- Helper Functions for Fragments & External Callers ---

    fun openWikiDrawer() {
        if (::drawerLayout.isInitialized) {
            drawerLayout.openDrawer(GravityCompat.START)
        }
    }

    fun pickWallpaper() {
        onPickImage.launch(arrayOf("image/*"))
    }

    fun requestVoicePermission() {
        requestVoice.launch(Manifest.permission.RECORD_AUDIO)
    }

    fun captureLogcat() {
        try {
            Debug(this).captureLogcat(isDeviceSecure())
            Toast.makeText(this, "Logcat tersimpan di folder Downloads", Toast.LENGTH_SHORT).show()
        } catch (e: IOException) {
            Toast.makeText(this, "Gagal menyimpan logcat: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun openDonation() {
        donationManager.onSendDonationClicked()
    }

    fun isDeviceSecure(): Boolean {
        return with(getSystemService(KEYGUARD_SERVICE) as KeyguardManager) { isDeviceSecure }
    }

    fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(
            this@CoverPreferences, permission
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun ignoreBatteryOptimization(): Boolean {
        return with(getSystemService(POWER_SERVICE) as PowerManager) {
            isIgnoringBatteryOptimizations(packageName)
        }
    }

    fun hasAccessibility(): Boolean {
        if (BuildConfig.GOOGLE_PLAY) return false
        val serviceString = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        return serviceString?.contains(
            packageName + File.separator + AccessibilityObserver::class.java.name
        ) == true
    }

    fun hasNotificationListener(): Boolean {
        val myNotificationListenerComponentName = ComponentName(
            applicationContext, NotificationReceiver::class.java
        )
        val enabledListeners = Settings.Secure.getString(
            contentResolver, "enabled_notification_listeners"
        )
        if (null == enabledListeners || enabledListeners.isEmpty()) return false
        return enabledListeners.split(":").map {
            ComponentName.unflattenFromString(it)
        }.any { componentName ->
            myNotificationListenerComponentName == componentName
        }
    }

    fun hasUsageStatistics(): Boolean {
        try {
            with(getSystemService(APP_OPS_SERVICE) as AppOpsManager) {
                unsafeCheckOp("android:get_usage_stats", Process.myUid(), packageName).run {
                    if (this == AppOpsManager.MODE_ALLOWED) return true
                }
            }
        } catch (_: SecurityException) { }
        return false
    }

    // --- Wallpaper Saving ---

    private fun saveAnimatedImage(sourceUri: Uri) {
        val background = File(filesDir, "wallpaper.png")
        if (background.exists()) background.delete()
        CoroutineScope(Dispatchers.IO).launch {
            val destinationFilename = File(filesDir, "wallpaper.gif")
            try {
                contentResolver.openInputStream(sourceUri).use { stream ->
                    FileOutputStream(destinationFilename, false).use {
                        stream?.copyTo(it)
                    }
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    private fun saveStaticImage(sourceUri: Uri) {
        val animated = File(filesDir, "wallpaper.gif")
        if (animated.exists()) animated.delete()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val source: ImageDecoder.Source = ImageDecoder.createSource(
                    this@CoverPreferences.contentResolver, sourceUri
                )
                val bitmap: Bitmap = ImageDecoder.decodeBitmap(source)
                var rotation = -1
                val background = File(filesDir, "wallpaper.png")
                contentResolver.query(
                    sourceUri, arrayOf(MediaStore.Images.ImageColumns.ORIENTATION),
                    null, null, null
                )?.use {
                    if (it.count == 1) {
                        it.moveToFirst()
                        rotation = it.getInt(0)
                    }
                }
                if (rotation > 0) {
                    val matrix = Matrix()
                    matrix.postRotate(rotation.toFloat())
                    background.writeBitmap(
                        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true),
                        Bitmap.CompressFormat.PNG, 100
                    )
                } else {
                    background.writeBitmap(bitmap, Bitmap.CompressFormat.PNG, 100)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun File.writeBitmap(bitmap: Bitmap, format: Bitmap.CompressFormat, quality: Int) {
        outputStream().use { out ->
            bitmap.compress(format, quality, out)
            out.flush()
        }
    }
}
