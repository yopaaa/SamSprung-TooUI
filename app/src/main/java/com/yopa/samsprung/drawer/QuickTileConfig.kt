package com.yopa.samsprung.drawer

import com.yopa.samsprung.R
import com.yopa.samsprung.settings.Preferences

data class QuickTileData(
    val id: String,
    val title: String,
    val description: String,
    val iconRes: Int,
    val prefKey: String
)

object QuickTileRegistry {
    val ALL_TILES = mapOf(
        "wifi" to QuickTileData(
            id = "wifi",
            title = "Wi-Fi",
            description = "Pintasan panel internet & koneksi Wi-Fi",
            iconRes = R.drawable.ic_wifi_on_24dp,
            prefKey = Preferences.prefQuickWifi
        ),
        "bluetooth" to QuickTileData(
            id = "bluetooth",
            title = "Bluetooth",
            description = "Pintasan sakelar konektivitas Bluetooth",
            iconRes = R.drawable.ic_bluetooth_on_24dp,
            prefKey = Preferences.prefQuickBluetooth
        ),
        "mobile_data" to QuickTileData(
            id = "mobile_data",
            title = "Data Seluler",
            description = "Pintasan panel jaringan data seluler",
            iconRes = R.drawable.ic_mobile_data_24dp,
            prefKey = Preferences.prefQuickMobileData
        ),
        "sound" to QuickTileData(
            id = "sound",
            title = "Profil Suara",
            description = "Beralih mode Dering, Getar, dan Hening",
            iconRes = R.drawable.ic_sound_on_24dp,
            prefKey = Preferences.prefQuickSound
        ),
        "rotation" to QuickTileData(
            id = "rotation",
            title = "Kunci Rotasi Layar",
            description = "Pintasan mengunci atau mengizinkan rotasi cover",
            iconRes = R.drawable.ic_screen_rotation_24,
            prefKey = Preferences.prefQuickRotation
        ),
        "torch" to QuickTileData(
            id = "torch",
            title = "Senter (Flashlight)",
            description = "Pintasan menyalakan lampu kilat senter Z Flip 4",
            iconRes = R.drawable.ic_flashlight_on_24dp,
            prefKey = Preferences.prefQuickTorch
        ),
        "airplane" to QuickTileData(
            id = "airplane",
            title = "Mode Pesawat",
            description = "Pintasan mengaktifkan mode pesawat terbang",
            iconRes = R.drawable.ic_airplanemode_24dp,
            prefKey = Preferences.prefQuickAirplane
        ),
        "hotspot" to QuickTileData(
            id = "hotspot",
            title = "Hotspot Seluler",
            description = "Pintasan tethering / hotspot seluler",
            iconRes = R.drawable.ic_wifi_tethering_24dp,
            prefKey = Preferences.prefQuickHotspot
        ),
        "dnd" to QuickTileData(
            id = "dnd",
            title = "Jangan Ganggu (DND)",
            description = "Pintasan mode hening total notifikasi",
            iconRes = R.drawable.ic_do_not_disturb_on_24dp,
            prefKey = Preferences.prefQuickDnd
        ),
        "nfc" to QuickTileData(
            id = "nfc",
            title = "NFC",
            description = "Pintasan sakelar sensor NFC",
            iconRes = R.drawable.ic_nfc_on_24dp,
            prefKey = Preferences.prefQuickNfc
        ),
        "device_care" to QuickTileData(
            id = "device_care",
            title = "Device Care",
            description = "Pintasan menuju Perawatan Perangkat Samsung",
            iconRes = R.drawable.ic_device_care_24dp,
            prefKey = Preferences.prefQuickDeviceCare
        ),
        "auth" to QuickTileData(
            id = "auth",
            title = "Kunci / Buka Biometrik",
            description = "Pintasan status kunci layar & autentikasi sidik jari",
            iconRes = R.drawable.ic_fingerprint_24dp,
            prefKey = Preferences.prefQuickAuth
        )
    )

    fun getOrderedTiles(orderString: String?): List<QuickTileData> {
        val rawOrder = if (!orderString.isNullOrBlank()) orderString else Preferences.DEFAULT_QUICK_TILES_ORDER
        val ids = rawOrder.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val result = mutableListOf<QuickTileData>()
        val seen = mutableSetOf<String>()

        for (id in ids) {
            ALL_TILES[id]?.let {
                result.add(it)
                seen.add(id)
            }
        }
        for ((id, tile) in ALL_TILES) {
            if (!seen.contains(id)) {
                result.add(tile)
            }
        }
        return result
    }
}
