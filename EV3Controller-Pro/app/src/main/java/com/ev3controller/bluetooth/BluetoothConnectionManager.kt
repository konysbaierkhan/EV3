package com.ev3controller.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.OutputStream
import java.util.UUID

/**
 * Состояния Bluetooth-соединения для отображения в интерфейсе.
 */
sealed class ConnectionState {
    data object Disconnected : ConnectionState()
    data object Connecting : ConnectionState()
    data class Connected(val deviceName: String, val address: String) : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}

/**
 * Менеджер Bluetooth Classic (SPP) соединения с блоком LEGO Mindstorms EV3.
 *
 * Управляет жизненным циклом RFCOMM-сокета, обеспечивает потокобезопасную
 * отправку пакетов EV3 Direct Commands и публикует статус через StateFlow.
 */
class BluetoothConnectionManager(context: Context) {

    companion object {
        private const val TAG = "Ev3BtManager"
        /** Стандартный UUID для Serial Port Profile (SPP) */
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    private var socket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null
    private val writeMutex = Mutex()

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    /** Доступен ли Bluetooth на смартфоне */
    val isBluetoothAvailable: Boolean get() = bluetoothAdapter != null

    /** Включён ли модуль Bluetooth */
    val isBluetoothEnabled: Boolean get() = bluetoothAdapter?.isEnabled == true

    /**
     * Возвращает список сопряжённых Bluetooth-устройств.
     * Роботы с именем "EV3" автоматически поднимаются в начало списка.
     */
    @SuppressLint("MissingPermission")
    fun getBondedDevices(): List<BluetoothDevice> {
        val bonded = bluetoothAdapter?.bondedDevices?.toList() ?: emptyList()
        return bonded.sortedWith(
            compareByDescending<BluetoothDevice> {
                (it.name ?: "").contains("EV3", ignoreCase = true)
            }.thenBy { it.name ?: it.address }
        )
    }

    /**
     * Подключиться к выбранному блоку EV3 по протоколу Bluetooth Classic SPP.
     * При ошибке защищённого RFCOMM-сокета автоматически пробует Insecure RFCOMM
     * для максимальной совместимости со всеми моделями смартфонов на уроке.
     */
    @SuppressLint("MissingPermission")
    suspend fun connect(device: BluetoothDevice) {
        if (_connectionState.value is ConnectionState.Connecting) return

        withContext(Dispatchers.IO) {
            _connectionState.value = ConnectionState.Connecting
            closeSocketSilently()

            try {
                if (bluetoothAdapter == null) {
                    _connectionState.value = ConnectionState.Error("Bluetooth не поддерживается на устройстве")
                    return@withContext
                }
                if (!bluetoothAdapter.isEnabled) {
                    _connectionState.value = ConnectionState.Error("Включите Bluetooth в настройках телефона")
                    return@withContext
                }

                // Отменяем поиск устройств, чтобы не замедлять установку соединения
                try {
                    bluetoothAdapter.cancelDiscovery()
                } catch (_: SecurityException) {}

                val deviceName = device.name ?: "EV3 (${device.address})"
                Log.d(TAG, "Подключение к $deviceName (${device.address})...")

                val connectedSocket = try {
                    val secureSocket = device.createRfcommSocketToServiceRecord(SPP_UUID)
                    secureSocket.connect()
                    secureSocket
                } catch (firstEx: IOException) {
                    Log.w(TAG, "Стандартный сокет не подключился, пробуем Insecure RFCOMM...", firstEx)
                    val insecureSocket = device.createInsecureRfcommSocketToServiceRecord(SPP_UUID)
                    insecureSocket.connect()
                    insecureSocket
                }

                socket = connectedSocket
                outputStream = connectedSocket.outputStream
                _connectionState.value = ConnectionState.Connected(deviceName, device.address)
                Log.d(TAG, "Успешно подключено к $deviceName")

            } catch (e: IOException) {
                Log.e(TAG, "Ошибка подключения: ${e.message}", e)
                closeSocketSilently()
                _connectionState.value = ConnectionState.Error(
                    "Не удалось подключиться к EV3. Проверьте, что блок включён и сопряжён по Bluetooth."
                )
            } catch (e: SecurityException) {
                Log.e(TAG, "Отсутствует разрешение Bluetooth: ${e.message}", e)
                closeSocketSilently()
                _connectionState.value = ConnectionState.Error("Нет разрешений Bluetooth")
            }
        }
    }

    /**
     * Отключиться от блока EV3 и освободить сокет.
     */
    suspend fun disconnect() {
        withContext(Dispatchers.IO) {
            closeSocketSilently()
            _connectionState.value = ConnectionState.Disconnected
            Log.d(TAG, "Отключено от EV3")
        }
    }

    /**
     * Потокобезопасная отправка одного или нескольких байтовых пакетов в EV3.
     */
    suspend fun sendCommand(vararg packets: ByteArray) {
        if (packets.isEmpty()) return
        writeMutex.withLock {
            withContext(Dispatchers.IO) {
                val stream = outputStream ?: return@withContext
                try {
                    for (packet in packets) {
                        stream.write(packet)
                    }
                    stream.flush()
                } catch (e: IOException) {
                    Log.e(TAG, "Ошибка передачи данных: ${e.message}", e)
                    closeSocketSilently()
                    _connectionState.value = ConnectionState.Error("Связь с роботом потеряна")
                }
            }
        }
    }

    /**
     * Немедленная отправка критичной команды (STOP / экстренный тормоз),
     * минуя любые ограничители частоты на уровне ViewModel.
     */
    suspend fun sendImmediate(vararg packets: ByteArray) {
        sendCommand(*packets)
    }

    private fun closeSocketSilently() {
        try {
            outputStream?.close()
        } catch (_: IOException) {}
        try {
            socket?.close()
        } catch (_: IOException) {}
        outputStream = null
        socket = null
    }
}
