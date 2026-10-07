package com.tengwear.appopspro.model

/**
 * AppOps 全量目录（Android 14 / API 34 实测导出，共 147 条）。
 *
 * 数据来源：`adb pull /system/framework/framework.jar` → `dexdump -d` 反汇编
 * `android.app.AppOpsManager.<clinit>`，逐条提取
 * `AppOpInfo$Builder(code, "android:xxx", "NAME")` 的构建参数。
 * 已用项目原有 28 条（经 `strOpToOp` 真机核对）做一致性校验，28/28 完全一致。
 *
 * ⚠️ opCode 与 Android 版本强相关，本表对应 API 34。
 *    Android 11(API 30) 的 op 数量少于本表，高位 code 在旧系统上不存在；
 *    个别 op 的历史编号还会变动（如 activity_recognition 曾为 47，34 起为 79）。
 *    若需支持多版本，应按下发版本选择目录，或改为运行期解析。
 */
object AppOpsCatalog {

    // ── 分组（用于权限列表分节展示，按重要度排序）──────────
    const val GROUP_LOCATION = "位置"
    const val GROUP_MEDIA = "媒体与音频"
    const val GROUP_STORAGE = "存储"
    const val GROUP_COMM = "通讯"
    const val GROUP_NETWORK = "网络与连接"
    const val GROUP_SENSOR = "传感器与生物识别"
    const val GROUP_SYSTEM = "系统与后台"

    /** 分组展示顺序。 */
    val GROUP_ORDER = listOf(
        GROUP_LOCATION, GROUP_MEDIA, GROUP_STORAGE,
        GROUP_COMM, GROUP_NETWORK, GROUP_SENSOR, GROUP_SYSTEM,
    )

    /**
     * 单条 AppOps 定义。
     *
     * @param code        op 编号（`getOpsForPackage` / `setMode` 使用）
     * @param opName      系统 op 字符串，如 `android:camera`
     * @param name        系统短名，如 `CAMERA`（`cmd appops` 里的显示形式）
     * @param label       中文显示名
     * @param group       所属分组，取值见 GROUP_* 常量
     * @param permission  对应的运行时权限；null 表示非运行时权限类 op
     * @param defaultMode 系统默认模式（0=allow 1=ignore 2=ask 3=default）；-1 未声明
     */
    data class Op(
        val code: Int,
        val opName: String,
        val name: String,
        val label: String,
        val group: String,
        val permission: String?,
        val defaultMode: Int,
    )

    /** 全量目录，按 opCode 升序。 */
    val ALL: List<Op> = listOf(
        Op(0, "android:coarse_location", "COARSE_LOCATION", "粗略位置", "位置", "android.permission.ACCESS_COARSE_LOCATION", 0),
        Op(1, "android:fine_location", "FINE_LOCATION", "精确位置", "位置", "android.permission.ACCESS_FINE_LOCATION", 0),
        Op(2, "android:gps", "GPS", "GPS 定位", "位置", null, 0),
        Op(3, "android:vibrate", "VIBRATE", "振动", "传感器与生物识别", "android.permission.VIBRATE", 0),
        Op(4, "android:read_contacts", "READ_CONTACTS", "读取联系人", "通讯", "android.permission.READ_CONTACTS", 0),
        Op(5, "android:write_contacts", "WRITE_CONTACTS", "修改联系人", "通讯", "android.permission.WRITE_CONTACTS", 0),
        Op(6, "android:read_call_log", "READ_CALL_LOG", "读取通话记录", "通讯", "android.permission.READ_CALL_LOG", 0),
        Op(7, "android:write_call_log", "WRITE_CALL_LOG", "修改通话记录", "通讯", "android.permission.WRITE_CALL_LOG", 0),
        Op(8, "android:read_calendar", "READ_CALENDAR", "读取日历", "通讯", "android.permission.READ_CALENDAR", 0),
        Op(9, "android:write_calendar", "WRITE_CALENDAR", "修改日历", "通讯", "android.permission.WRITE_CALENDAR", 0),
        Op(10, "android:wifi_scan", "WIFI_SCAN", "扫描 Wi-Fi", "网络与连接", "android.permission.ACCESS_WIFI_STATE", 0),
        Op(11, "android:post_notification", "POST_NOTIFICATION", "发送通知", "系统与后台", "android.permission.POST_NOTIFICATIONS", 0),
        Op(12, "android:neighboring_cells", "NEIGHBORING_CELLS", "邻近基站", "网络与连接", null, 0),
        Op(13, "android:call_phone", "CALL_PHONE", "拨打电话", "通讯", "android.permission.CALL_PHONE", 0),
        Op(14, "android:read_sms", "READ_SMS", "读取短信", "通讯", "android.permission.READ_SMS", 0),
        Op(15, "android:write_sms", "WRITE_SMS", "写入短信", "通讯", null, 1),
        Op(16, "android:receive_sms", "RECEIVE_SMS", "接收短信", "通讯", "android.permission.RECEIVE_SMS", 0),
        Op(17, "android:receive_emergency_broadcast", "RECEIVE_EMERGENCY_BROADCAST", "接收紧急广播", "通讯", "android.permission.RECEIVE_EMERGENCY_BROADCAST", 0),
        Op(18, "android:receive_mms", "RECEIVE_MMS", "接收彩信", "通讯", "android.permission.RECEIVE_MMS", 0),
        Op(19, "android:receive_wap_push", "RECEIVE_WAP_PUSH", "接收 WAP 推送", "通讯", "android.permission.RECEIVE_WAP_PUSH", 0),
        Op(20, "android:send_sms", "SEND_SMS", "发送短信", "通讯", "android.permission.SEND_SMS", 0),
        Op(21, "android:read_icc_sms", "READ_ICC_SMS", "读取 SIM 短信", "通讯", "android.permission.READ_SMS", 0),
        Op(22, "android:write_icc_sms", "WRITE_ICC_SMS", "写入 SIM 短信", "通讯", null, 0),
        Op(23, "android:write_settings", "WRITE_SETTINGS", "修改系统设置", "系统与后台", "android.permission.WRITE_SETTINGS", -1),
        Op(24, "android:system_alert_window", "SYSTEM_ALERT_WINDOW", "悬浮窗", "系统与后台", "android.permission.SYSTEM_ALERT_WINDOW", -1),
        Op(25, "android:access_notifications", "ACCESS_NOTIFICATIONS", "访问通知", "系统与后台", "android.permission.ACCESS_NOTIFICATIONS", 0),
        Op(26, "android:camera", "CAMERA", "相机", "媒体与音频", "android.permission.CAMERA", 0),
        Op(27, "android:record_audio", "RECORD_AUDIO", "录音", "媒体与音频", "android.permission.RECORD_AUDIO", 0),
        Op(28, "android:play_audio", "PLAY_AUDIO", "播放音频", "媒体与音频", null, 0),
        Op(29, "android:read_clipboard", "READ_CLIPBOARD", "读取剪贴板", "系统与后台", null, 0),
        Op(30, "android:write_clipboard", "WRITE_CLIPBOARD", "写入剪贴板", "系统与后台", null, 0),
        Op(31, "android:take_media_buttons", "TAKE_MEDIA_BUTTONS", "媒体按键", "媒体与音频", null, 0),
        Op(32, "android:take_audio_focus", "TAKE_AUDIO_FOCUS", "音频焦点", "媒体与音频", null, 0),
        Op(33, "android:audio_master_volume", "AUDIO_MASTER_VOLUME", "主音量", "媒体与音频", null, 0),
        Op(34, "android:audio_voice_volume", "AUDIO_VOICE_VOLUME", "通话音量", "媒体与音频", null, 0),
        Op(35, "android:audio_ring_volume", "AUDIO_RING_VOLUME", "铃声音量", "媒体与音频", null, 0),
        Op(36, "android:audio_media_volume", "AUDIO_MEDIA_VOLUME", "媒体音量", "媒体与音频", null, 0),
        Op(37, "android:audio_alarm_volume", "AUDIO_ALARM_VOLUME", "闹钟音量", "媒体与音频", null, 0),
        Op(38, "android:audio_notification_volume", "AUDIO_NOTIFICATION_VOLUME", "通知音量", "媒体与音频", null, 0),
        Op(39, "android:audio_bluetooth_volume", "AUDIO_BLUETOOTH_VOLUME", "蓝牙音量", "媒体与音频", null, 0),
        Op(40, "android:wake_lock", "WAKE_LOCK", "保持唤醒", "系统与后台", "android.permission.WAKE_LOCK", 0),
        Op(41, "android:monitor_location", "MONITOR_LOCATION", "监听位置", "位置", null, 0),
        Op(42, "android:monitor_location_high_power", "MONITOR_HIGH_POWER_LOCATION", "高精度监听位置", "位置", null, 0),
        Op(43, "android:get_usage_stats", "GET_USAGE_STATS", "使用统计", "系统与后台", "android.permission.PACKAGE_USAGE_STATS", -1),
        Op(44, "android:mute_microphone", "MUTE_MICROPHONE", "静音麦克风", "媒体与音频", null, 0),
        Op(45, "android:toast_window", "TOAST_WINDOW", "弹窗提示", "系统与后台", null, 0),
        Op(46, "android:project_media", "PROJECT_MEDIA", "投射媒体", "媒体与音频", null, 1),
        Op(47, "android:activate_vpn", "ACTIVATE_VPN", "启用 VPN", "网络与连接", null, 1),
        Op(48, "android:write_wallpaper", "WRITE_WALLPAPER", "设置壁纸", "系统与后台", null, 0),
        Op(49, "android:assist_structure", "ASSIST_STRUCTURE", "辅助结构", "媒体与音频", null, 0),
        Op(50, "android:assist_screenshot", "ASSIST_SCREENSHOT", "辅助截图", "媒体与音频", null, 0),
        Op(51, "android:read_phone_state", "READ_PHONE_STATE", "读取手机状态", "通讯", "android.permission.READ_PHONE_STATE", 0),
        Op(52, "android:add_voicemail", "ADD_VOICEMAIL", "添加语音信箱", "通讯", "com.android.voicemail.permission.ADD_VOICEMAIL", 0),
        Op(53, "android:use_sip", "USE_SIP", "使用 SIP", "网络与连接", "android.permission.USE_SIP", 0),
        Op(54, "android:process_outgoing_calls", "PROCESS_OUTGOING_CALLS", "处理去电", "通讯", "android.permission.PROCESS_OUTGOING_CALLS", 0),
        Op(55, "android:use_fingerprint", "USE_FINGERPRINT", "指纹识别", "传感器与生物识别", "android.permission.USE_FINGERPRINT", 0),
        Op(56, "android:body_sensors", "BODY_SENSORS", "身体传感器", "传感器与生物识别", "android.permission.BODY_SENSORS", 0),
        Op(57, "android:read_cell_broadcasts", "READ_CELL_BROADCASTS", "读取小区广播", "网络与连接", "android.permission.READ_CELL_BROADCASTS", 0),
        Op(58, "android:mock_location", "MOCK_LOCATION", "模拟位置", "位置", null, 2),
        Op(59, "android:read_external_storage", "READ_EXTERNAL_STORAGE", "读取存储", "存储", "android.permission.READ_EXTERNAL_STORAGE", 0),
        Op(60, "android:write_external_storage", "WRITE_EXTERNAL_STORAGE", "写入存储", "存储", "android.permission.WRITE_EXTERNAL_STORAGE", 0),
        Op(61, "android:turn_screen_on", "TURN_SCREEN_ON", "点亮屏幕", "系统与后台", "android.permission.TURN_SCREEN_ON", 3),
        Op(62, "android:get_accounts", "GET_ACCOUNTS", "读取账户", "通讯", "android.permission.GET_ACCOUNTS", 0),
        Op(63, "android:run_in_background", "RUN_IN_BACKGROUND", "后台运行", "系统与后台", null, 0),
        Op(64, "android:audio_accessibility_volume", "AUDIO_ACCESSIBILITY_VOLUME", "无障碍音量", "媒体与音频", null, 0),
        Op(65, "android:read_phone_numbers", "READ_PHONE_NUMBERS", "读取号码", "通讯", "android.permission.READ_PHONE_NUMBERS", 0),
        Op(66, "android:request_install_packages", "REQUEST_INSTALL_PACKAGES", "请求安装应用", "系统与后台", "android.permission.REQUEST_INSTALL_PACKAGES", -1),
        Op(67, "android:picture_in_picture", "PICTURE_IN_PICTURE", "画中画", "媒体与音频", null, 0),
        Op(68, "android:instant_app_start_foreground", "INSTANT_APP_START_FOREGROUND", "免安装应用前台", "系统与后台", "android.permission.INSTANT_APP_FOREGROUND_SERVICE", -1),
        Op(69, "android:answer_phone_calls", "ANSWER_PHONE_CALLS", "接听电话", "通讯", "android.permission.ANSWER_PHONE_CALLS", 0),
        Op(70, "android:run_any_in_background", "RUN_ANY_IN_BACKGROUND", "任意后台运行", "系统与后台", null, 0),
        Op(71, "android:change_wifi_state", "CHANGE_WIFI_STATE", "修改 Wi-Fi 状态", "网络与连接", "android.permission.CHANGE_WIFI_STATE", 0),
        Op(72, "android:request_delete_packages", "REQUEST_DELETE_PACKAGES", "请求卸载应用", "系统与后台", "android.permission.REQUEST_DELETE_PACKAGES", 0),
        Op(73, "android:bind_accessibility_service", "BIND_ACCESSIBILITY_SERVICE", "绑定无障碍服务", "系统与后台", "android.permission.BIND_ACCESSIBILITY_SERVICE", 0),
        Op(74, "android:accept_handover", "ACCEPT_HANDOVER", "接受网络切换", "网络与连接", "android.permission.ACCEPT_HANDOVER", 0),
        Op(75, "android:manage_ipsec_tunnels", "MANAGE_IPSEC_TUNNELS", "管理 IPsec 隧道", "网络与连接", "android.permission.MANAGE_IPSEC_TUNNELS", 2),
        Op(76, "android:start_foreground", "START_FOREGROUND", "启动前台服务", "系统与后台", "android.permission.FOREGROUND_SERVICE", 0),
        Op(77, "android:bluetooth_scan", "BLUETOOTH_SCAN", "扫描蓝牙", "网络与连接", "android.permission.BLUETOOTH_SCAN", 0),
        Op(78, "android:use_biometric", "USE_BIOMETRIC", "生物识别", "传感器与生物识别", "android.permission.USE_BIOMETRIC", 0),
        Op(79, "android:activity_recognition", "ACTIVITY_RECOGNITION", "活动识别", "传感器与生物识别", "android.permission.ACTIVITY_RECOGNITION", 0),
        Op(80, "android:sms_financial_transactions", "SMS_FINANCIAL_TRANSACTIONS", "金融短信", "通讯", "android.permission.SMS_FINANCIAL_TRANSACTIONS", -1),
        Op(81, "android:read_media_audio", "READ_MEDIA_AUDIO", "读取音频", "存储", "android.permission.READ_MEDIA_AUDIO", 0),
        Op(82, "android:write_media_audio", "WRITE_MEDIA_AUDIO", "写入音频", "存储", null, 2),
        Op(83, "android:read_media_video", "READ_MEDIA_VIDEO", "读取视频", "存储", "android.permission.READ_MEDIA_VIDEO", 0),
        Op(84, "android:write_media_video", "WRITE_MEDIA_VIDEO", "写入视频", "存储", null, 2),
        Op(85, "android:read_media_images", "READ_MEDIA_IMAGES", "读取图片", "存储", "android.permission.READ_MEDIA_IMAGES", 0),
        Op(86, "android:write_media_images", "WRITE_MEDIA_IMAGES", "写入图片", "存储", null, 2),
        Op(87, "android:legacy_storage", "LEGACY_STORAGE", "旧版存储", "存储", null, -1),
        Op(88, "android:access_accessibility", "ACCESS_ACCESSIBILITY", "访问无障碍", "系统与后台", null, 0),
        Op(89, "android:read_device_identifiers", "READ_DEVICE_IDENTIFIERS", "读取设备标识", "系统与后台", null, 2),
        Op(90, "android:access_media_location", "ACCESS_MEDIA_LOCATION", "访问媒体位置", "位置", "android.permission.ACCESS_MEDIA_LOCATION", 0),
        Op(91, "android:query_all_packages", "QUERY_ALL_PACKAGES", "查询所有应用", "系统与后台", null, -1),
        Op(92, "android:manage_external_storage", "MANAGE_EXTERNAL_STORAGE", "所有文件访问", "存储", "android.permission.MANAGE_EXTERNAL_STORAGE", -1),
        Op(93, "android:interact_across_profiles", "INTERACT_ACROSS_PROFILES", "跨配置文件交互", "通讯", "android.permission.INTERACT_ACROSS_PROFILES", -1),
        Op(94, "android:activate_platform_vpn", "ACTIVATE_PLATFORM_VPN", "启用平台 VPN", "网络与连接", null, 1),
        Op(95, "android:loader_usage_stats", "LOADER_USAGE_STATS", "加载器使用统计", "系统与后台", "android.permission.LOADER_USAGE_STATS", -1),
        Op(97, "android:auto_revoke_permissions_if_unused", "AUTO_REVOKE_PERMISSIONS_IF_UNUSED", "自动撤销闲置权限", "系统与后台", null, -1),
        Op(98, "android:auto_revoke_managed_by_installer", "AUTO_REVOKE_MANAGED_BY_INSTALLER", "安装器管理自动撤销", "系统与后台", null, 0),
        Op(99, "android:no_isolated_storage", "NO_ISOLATED_STORAGE", "禁用隔离存储", "存储", null, 2),
        Op(100, "android:phone_call_microphone", "PHONE_CALL_MICROPHONE", "通话麦克风", "通讯", null, 0),
        Op(101, "android:phone_call_camera", "PHONE_CALL_CAMERA", "通话相机", "通讯", null, 0),
        Op(102, "android:record_audio_hotword", "RECORD_AUDIO_HOTWORD", "热词录音", "媒体与音频", null, 0),
        Op(103, "android:manage_ongoing_calls", "MANAGE_ONGOING_CALLS", "管理通话", "通讯", "android.permission.MANAGE_ONGOING_CALLS", -1),
        Op(104, "android:manage_credentials", "MANAGE_CREDENTIALS", "管理凭据", "系统与后台", null, -1),
        Op(105, "android:use_icc_auth_with_device_identifier", "USE_ICC_AUTH_WITH_DEVICE_IDENTIFIER", "SIM 身份认证", "通讯", "android.permission.USE_ICC_AUTH_WITH_DEVICE_IDENTIFIER", -1),
        Op(106, "android:record_audio_output", "RECORD_AUDIO_OUTPUT", "录制音频输出", "媒体与音频", null, 0),
        Op(107, "android:schedule_exact_alarm", "SCHEDULE_EXACT_ALARM", "精确闹钟", "系统与后台", "android.permission.SCHEDULE_EXACT_ALARM", -1),
        Op(108, "android:fine_location_source", "FINE_LOCATION_SOURCE", "精确位置来源", "位置", null, 0),
        Op(109, "android:coarse_location_source", "COARSE_LOCATION_SOURCE", "粗略位置来源", "位置", null, 0),
        Op(110, "android:manage_media", "MANAGE_MEDIA", "管理媒体", "存储", "android.permission.MANAGE_MEDIA", -1),
        Op(111, "android:bluetooth_connect", "BLUETOOTH_CONNECT", "连接蓝牙", "网络与连接", "android.permission.BLUETOOTH_CONNECT", 0),
        Op(112, "android:uwb_ranging", "UWB_RANGING", "UWB 测距", "传感器与生物识别", "android.permission.UWB_RANGING", 0),
        Op(113, "android:activity_recognition_source", "ACTIVITY_RECOGNITION_SOURCE", "活动识别来源", "传感器与生物识别", null, 0),
        Op(114, "android:bluetooth_advertise", "BLUETOOTH_ADVERTISE", "蓝牙广播", "网络与连接", "android.permission.BLUETOOTH_ADVERTISE", 0),
        Op(115, "android:record_incoming_phone_audio", "RECORD_INCOMING_PHONE_AUDIO", "录制来电音频", "通讯", null, 0),
        Op(116, "android:nearby_wifi_devices", "NEARBY_WIFI_DEVICES", "附近 Wi-Fi 设备", "网络与连接", "android.permission.NEARBY_WIFI_DEVICES", 0),
        Op(117, "android:establish_vpn_service", "ESTABLISH_VPN_SERVICE", "建立 VPN 服务", "网络与连接", null, 0),
        Op(118, "android:establish_vpn_manager", "ESTABLISH_VPN_MANAGER", "建立 VPN 管理", "网络与连接", null, 0),
        Op(119, "android:access_restricted_settings", "ACCESS_RESTRICTED_SETTINGS", "访问受限设置", "系统与后台", null, 0),
        Op(120, "android:receive_ambient_trigger_audio", "RECEIVE_SOUNDTRIGGER_AUDIO", "环境触发音频", "媒体与音频", null, 0),
        Op(121, "android:receive_explicit_user_interaction_audio", "RECEIVE_EXPLICIT_USER_INTERACTION_AUDIO", "用户交互音频", "媒体与音频", null, 0),
        Op(122, "android:run_user_initiated_jobs", "RUN_USER_INITIATED_JOBS", "用户发起任务", "系统与后台", null, 0),
        Op(123, "android:read_media_visual_user_selected", "READ_MEDIA_VISUAL_USER_SELECTED", "读取选定媒体", "存储", "android.permission.READ_MEDIA_VISUAL_USER_SELECTED", 0),
        Op(124, "android:system_exempt_from_suspension", "SYSTEM_EXEMPT_FROM_SUSPENSION", "豁免挂起", "系统与后台", null, -1),
        Op(125, "android:system_exempt_from_dismissible_notifications", "SYSTEM_EXEMPT_FROM_DISMISSIBLE_NOTIFICATIONS", "豁免可关闭通知", "系统与后台", null, -1),
        Op(126, "android:read_write_health_data", "READ_WRITE_HEALTH_DATA", "读写健康数据", "传感器与生物识别", null, 0),
        Op(127, "android:foreground_service_special_use", "FOREGROUND_SERVICE_SPECIAL_USE", "前台服务特殊用途", "系统与后台", "android.permission.FOREGROUND_SERVICE_SPECIAL_USE", -1),
        Op(128, "android:system_exempt_from_power_restrictions", "SYSTEM_EXEMPT_FROM_POWER_RESTRICTIONS", "豁免省电限制", "系统与后台", null, -1),
        Op(129, "android:system_exempt_from_hibernation", "SYSTEM_EXEMPT_FROM_HIBERNATION", "豁免休眠", "系统与后台", null, -1),
        Op(130, "android:system_exempt_from_activity_bg_start_restriction", "SYSTEM_EXEMPT_FROM_ACTIVITY_BG_START_RESTRICTION", "豁免后台启动限制", "系统与后台", null, -1),
        Op(131, "android:capture_consentless_bugreport_on_userdebug_build", "CAPTURE_CONSENTLESS_BUGREPORT_ON_USERDEBUG_BUILD", "免同意抓取日志", "系统与后台", "android.permission.CAPTURE_CONSENTLESS_BUGREPORT_ON_USERDEBUG_BUILD", -1),
        Op(132, "android:deprecated_2", "DEPRECATED_2", "已废弃项", "系统与后台", null, 1),
        Op(133, "android:use_full_screen_intent", "USE_FULL_SCREEN_INTENT", "全屏 Intent", "系统与后台", "android.permission.USE_FULL_SCREEN_INTENT", -1),
        Op(134, "android:camera_sandboxed", "CAMERA_SANDBOXED", "沙箱相机", "媒体与音频", null, 0),
        Op(135, "android:record_audio_sandboxed", "RECORD_AUDIO_SANDBOXED", "沙箱录音", "媒体与音频", null, 0),
        Op(136, "android:receive_sandbox_trigger_audio", "RECEIVE_SANDBOX_TRIGGER_AUDIO", "沙箱触发音频", "媒体与音频", "android.permission.RECEIVE_SANDBOX_TRIGGER_AUDIO", 3),
        Op(137, "android:RECEIVE_SANDBOXED_DETECTION_TRAINING_DATA", "RECEIVE_SANDBOXED_DETECTION_TRAINING_DATA", "沙箱检测训练数据", "媒体与音频", "android.permission.RECEIVE_SANDBOXED_DETECTION_TRAINING_DATA", 3),
        Op(138, "android:create_accessibility_overlay", "CREATE_ACCESSIBILITY_OVERLAY", "创建无障碍浮层", "系统与后台", null, 0),
        Op(139, "android:media_routing_control", "MEDIA_ROUTING_CONTROL", "媒体路由控制", "媒体与音频", "android.permission.MEDIA_ROUTING_CONTROL", -1),
        Op(140, "android:enable_mobile_data_by_user", "ENABLE_MOBILE_DATA_BY_USER", "启用移动数据", "网络与连接", null, 0),
        Op(141, "android:reserved_for_testing", "OP_RESERVED_FOR_TESTING", "保留测试项", "系统与后台", null, 0),
        Op(142, "android:rapid_clear_notifications_by_listener", "RAPID_CLEAR_NOTIFICATIONS_BY_LISTENER", "监听器快速清通知", "系统与后台", null, 0),
        Op(143, "android:read_system_grammatical_gender", "READ_SYSTEM_GRAMMATICAL_GENDER", "读取语法性别", "系统与后台", null, -1),
        Op(144, "android:run_backup_jobs", "RUN_BACKUP_JOBS", "运行备份任务", "系统与后台", "android.permission.RUN_BACKUP_JOBS", -1),
        Op(145, "android:archive_icon_overlay", "ARCHIVE_ICON_OVERLAY", "归档图标浮层", "系统与后台", null, 0),
        Op(146, "android:unarchival_support", "UNARCHIVAL_CONFIRMATION", "取消归档", "系统与后台", null, 0),
        Op(147, "android:emergency_location", "EMERGENCY_LOCATION", "紧急位置", "位置", "android.permission.LOCATION_BYPASS", -1),
    )

    /** opCode → Op，供 Parcel 回读结果反查。 */
    val BY_CODE: Map<Int, Op> = ALL.associateBy { it.code }

    /** opName → Op。 */
    val BY_NAME: Map<String, Op> = ALL.associateBy { it.opName }

    /** 全量 opCode 数组，可直接喂给 `getOpsForPackage` 一次取回全部条目。 */
    val ALL_CODES: IntArray = ALL.map { it.code }.toIntArray()

    /** 按 [GROUP_ORDER] 分组；空分组不返回。 */
    fun grouped(): List<Pair<String, List<Op>>> =
        GROUP_ORDER.map { g -> g to ALL.filter { it.group == g } }
            .filter { it.second.isNotEmpty() }

    /** 中文显示名；未收录时回退为系统短名。 */
    fun labelOf(opName: String): String = BY_NAME[opName]?.label
        ?: opName.substringAfterLast(':').replace('_', ' ')
}
