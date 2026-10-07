package com.tengwear.appopspro.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ScreenShare
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.wear.compose.material3.MaterialTheme
import com.tengwear.appopspro.model.AppOpInfo
import com.tengwear.appopspro.model.AppOpsCatalog

/**
 * AppOp 的视觉表现层：图标 + 状态文案 + 状态配色。
 *
 * 把「权限标识」翻译成 Wear M3E 主题下的视觉语言，
 * 状态色全部取自 [MaterialTheme.colorScheme]，从而自动跟随动态取色（M3E）。
 */

/** 单个 AppOp 状态的视觉描述。 */
data class AppOpStatus(
    /** 简短状态文案，如「允许」「拒绝」。 */
    val label: String,
    /** 状态色，用于徽标与开关强调。 */
    val color: Color,
    /** 该状态是否属于「用户已显式设置」，否则为系统默认。 */
    val isExplicit: Boolean
)

/** 根据 [AppOpInfo.mode] 解析状态视觉描述。 */
@Composable
fun appOpStatusOf(mode: Int): AppOpStatus {
    val scheme = MaterialTheme.colorScheme
    return when (mode) {
        AppOpInfo.MODE_ALLOWED -> AppOpStatus("允许", scheme.primary, true)
        AppOpInfo.MODE_IGNORED -> AppOpStatus("拒绝", scheme.error, true)
        AppOpInfo.MODE_ASK -> AppOpStatus("询问", scheme.tertiary, true)
        else -> AppOpStatus("默认", scheme.outline, false)
    }
}

/**
 * 把 AppOp 名称映射到最贴切的 Material 图标。
 *
 * 策略是「精确覆盖 + 分组回退」：常见 op 逐个指定语义图标，
 * 其余交给 [iconForGroup] 按 [AppOpsCatalog] 的分组给一个不至于跑题的图标。
 * 这样新增 op 时不必再逐个补图标，也不会出现空白图标位。
 */
fun iconForAppOp(opName: String): ImageVector = when (opName) {
    // ── 位置 ──
    "android:coarse_location" -> Icons.Filled.LocationOn
    "android:fine_location" -> Icons.Filled.MyLocation
    "android:mock_location" -> Icons.Filled.DirectionsCar

    // ── 媒体与音频 ──
    "android:camera",
    "android:phone_call_camera" -> Icons.Filled.CameraAlt
    "android:record_audio",
    "android:record_audio_hotword",
    "android:phone_call_microphone" -> Icons.Filled.Mic
    "android:take_audio_focus",
    "android:play_audio" -> Icons.Filled.GraphicEq
    "android:audio_master_volume",
    "android:audio_media_volume",
    "android:audio_ring_volume",
    "android:audio_alarm_volume",
    "android:audio_notification_volume",
    "android:audio_voice_volume",
    "android:audio_bluetooth_volume",
    "android:audio_accessibility_volume" -> Icons.Filled.VolumeUp
    "android:project_media" -> Icons.AutoMirrored.Filled.ScreenShare
    "android:picture_in_picture" -> Icons.Filled.PictureInPicture

    // ── 存储 ──
    "android:read_external_storage",
    "android:legacy_storage" -> Icons.Filled.Folder
    "android:write_external_storage",
    "android:manage_external_storage" -> Icons.Filled.DriveFileRenameOutline
    "android:read_media_images",
    "android:read_media_video",
    "android:read_media_visual_user_selected" -> Icons.Filled.PhotoLibrary
    "android:read_media_audio" -> Icons.Filled.Movie

    // ── 通讯 ──
    "android:read_contacts",
    "android:write_contacts" -> Icons.Filled.Contacts
    "android:read_calendar",
    "android:write_calendar" -> Icons.Filled.CalendarMonth
    "android:read_call_log",
    "android:write_call_log",
    "android:process_outgoing_calls" -> Icons.Filled.PhoneInTalk
    "android:call_phone",
    "android:answer_phone_calls" -> Icons.Filled.Phone
    "android:read_phone_state" -> Icons.Filled.Badge
    "android:read_phone_numbers",
    "android:add_voicemail" -> Icons.Filled.Person
    "android:get_accounts" -> Icons.Filled.AccountCircle
    "android:read_sms",
    "android:send_sms",
    "android:receive_sms",
    "android:read_icc_sms",
    "android:receive_mms",
    "android:sms_financial_transactions" -> Icons.Filled.Sms

    // ── 网络与连接 ──
    "android:wifi_scan",
    "android:change_wifi_state",
    "android:nearby_wifi_devices" -> Icons.Filled.Wifi
    "android:bluetooth_scan",
    "android:bluetooth_connect",
    "android:bluetooth_advertise" -> Icons.Filled.Bluetooth
    "android:activate_vpn",
    "android:activate_platform_vpn",
    "android:manage_ipsec_tunnels" -> Icons.Filled.VpnKey
    "android:neighboring_cells",
    "android:read_cell_broadcasts" -> Icons.Filled.SignalCellularAlt

    // ── 传感器与生物识别 ──
    "android:vibrate" -> Icons.Filled.Vibration
    "android:body_sensors",
    "android:uwb_ranging" -> Icons.Filled.Sensors
    "android:activity_recognition",
    "android:activity_recognition_source" -> Icons.Filled.DirectionsRun
    "android:use_biometric",
    "android:use_fingerprint" -> Icons.Filled.Fingerprint
    "android:read_write_health_data" -> Icons.Filled.MonitorHeart

    // ── 系统与后台 ──
    "android:post_notification",
    "android:access_notifications" -> Icons.Filled.Notifications
    "android:get_usage_stats" -> Icons.Filled.Visibility
    "android:request_install_packages",
    "android:request_delete_packages" -> Icons.Filled.Download
    "android:write_settings",
    "android:write_secure_settings",
    "android:access_restricted_settings" -> Icons.Filled.Settings
    "android:wake_lock",
    "android:turn_screen_on" -> Icons.Filled.PowerSettingsNew
    "android:schedule_exact_alarm" -> Icons.Filled.Alarm
    "android:manage_credentials",
    "android:use_icc_auth_with_device_identifier" -> Icons.Filled.Lock
    "android:run_in_background",
    "android:run_any_in_background",
    "android:start_foreground",
    "android:instant_app_start_foreground" -> Icons.Filled.Speed
    "android:query_all_packages" -> Icons.Filled.Apps

    else -> iconForGroup(AppOpsCatalog.BY_NAME[opName]?.group)
}

/**
 * 分组级图标回退：当某个 op 没有精确指定图标时，按其所属分组给一个语义相近的图标，
 * 避免整屏都是 [Icons.Filled.Tune] 这类无信息量的占位图标。
 */
private fun iconForGroup(group: String?): ImageVector = when (group) {
    AppOpsCatalog.GROUP_LOCATION -> Icons.Filled.LocationOn
    AppOpsCatalog.GROUP_MEDIA -> Icons.Filled.GraphicEq
    AppOpsCatalog.GROUP_STORAGE -> Icons.Filled.Folder
    AppOpsCatalog.GROUP_COMM -> Icons.Filled.PhoneInTalk
    AppOpsCatalog.GROUP_NETWORK -> Icons.Filled.Wifi
    AppOpsCatalog.GROUP_SENSOR -> Icons.Filled.Sensors
    else -> Icons.Filled.Tune
}
