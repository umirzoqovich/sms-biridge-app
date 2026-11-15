package uz.smsbridge.pro.util

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import uz.smsbridge.pro.data.model.SmsLogEntry

object SmsBridgeEvents {

    private val _isServiceRunning = MutableStateFlow(false)
    val isServiceRunning = _isServiceRunning.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected = _isConnected.asStateFlow()

    private val _newLog = MutableSharedFlow<SmsLogEntry>(extraBufferCapacity = 20)
    val newLog = _newLog.asSharedFlow()

    private val _statsUpdated = MutableSharedFlow<Unit>(extraBufferCapacity = 5)
    val statsUpdated = _statsUpdated.asSharedFlow()

    fun setServiceRunning(running: Boolean) {
        _isServiceRunning.value = running
    }

    fun setConnected(connected: Boolean) {
        _isConnected.value = connected
    }

    fun emitNewLog(entry: SmsLogEntry) {
        _newLog.tryEmit(entry)
    }

    fun notifyStatsUpdated() {
        _statsUpdated.tryEmit(Unit)
    }
}
