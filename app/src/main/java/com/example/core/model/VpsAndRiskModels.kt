package com.example.core.model

enum class VpsStatus {
    PROVISIONING,
    RUNNING,
    STOPPED,
    REBOOTING,
    ERROR
}

data class VpsInstance(
    val id: String,
    val name: String,
    val planName: String = "Ultra Low Latency VPS (London LD4)",
    val ipAddress: String,
    val os: String = "Windows Server 2022 Datacenter",
    val cpuCores: Int = 4,
    val ramGb: Int = 8,
    val ssdGb: Int = 160,
    val region: String = "London, UK (0.8ms to Equinix)",
    val status: VpsStatus = VpsStatus.RUNNING,
    val mt5TerminalStatus: String = "MT5 Build 4480 Active",
    val uptimeHours: Int = 412,
    val cpuUsagePercent: Int = 14,
    val ramUsagePercent: Int = 38,
    val lastHeartbeatUtc: String
)

data class VpsLogEntry(
    val timestampUtc: String,
    val level: String, // "INFO", "WARN", "SECURITY", "MQL5"
    val message: String
)

enum class CommandType {
    PAUSE,
    RESUME,
    STOP,
    EMERGENCY_STOP
}

enum class CommandState {
    PENDING,
    DELIVERED,
    APPLIED,
    REJECTED,
    EXPIRED,
    FAILED
}

data class RemoteCommand(
    val id: String,
    val targetType: String, // "EA_INSTALLATION" or "BROKER_ACCOUNT"
    val targetId: String,
    val commandType: CommandType,
    val state: CommandState,
    val payloadJson: String = "{}",
    val createdAtUtc: String,
    val acknowledgedAtUtc: String? = null,
    val rejectionReason: String? = null
)

data class AppNotification(
    val id: String,
    val title: String,
    val message: String,
    val type: NotificationType,
    val timestampUtc: String,
    val read: Boolean = false
)

enum class NotificationType {
    TRADE_EVENT,
    CONFIG_APPLIED,
    CONFIG_REJECTED,
    RISK_WARNING,
    HEARTBEAT_ALERT,
    VPS_STATUS,
    SYSTEM_SECURITY
}
