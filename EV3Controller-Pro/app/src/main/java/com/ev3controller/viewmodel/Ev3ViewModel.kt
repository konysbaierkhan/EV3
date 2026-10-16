package com.ev3controller.viewmodel

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothDevice
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ev3controller.bluetooth.BluetoothConnectionManager
import com.ev3controller.bluetooth.ChassisConfig
import com.ev3controller.bluetooth.CommandThrottler
import com.ev3controller.bluetooth.ConnectionState
import com.ev3controller.bluetooth.Ev3Port
import com.ev3controller.bluetooth.Ev3Protocol
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Три основных режима управления роботом EV3.
 */
enum class ControlMode(val title: String, val shortTitle: String) {
    JOYSTICK_360("Джойстик 360°", "Джойстик 360°"),
    DPAD("Крестовина (D-Pad)", "Крестовина"),
    LAB_4_MOTORS("Лаборатория (4 мотора)", "Лаборатория")
}

/**
 * Быстрые пресеты ограничителя скорости для школьных уроков робототехники.
 */
enum class SpeedPreset(val label: String, val speedPercent: Int) {
    EDUCATIONAL("🐢 Учебный (40%)", 40),
    NORMAL("🚗 Обычный (70%)", 70),
    TURBO("🚀 Турбо (100%)", 100)
}

/**
 * ViewModel Pro-версии контроллера LEGO Mindstorms EV3.
 *
 * Реализует:
 * - Танковое/аркадное микширование для 360° джойстика
 * - Управление крестовиной (D-Pad) и дополнительными моторами (A / D)
 * - Лабораторный режим независимого тестирования 4 портов (A, B, C, D)
 * - Настройки скорости, инверсии всех 4 портов, выбора портов шасси и режима торможения (Brake/Coast)
 * - Звуковую панель (тест связи + мелодия старта гонки из 3 тонов)
 * - Троттлинг команд движения (80 мс) с мгновенным обходом для команд STOP
 */
class Ev3ViewModel(application: Application) : AndroidViewModel(application) {

    val btManager = BluetoothConnectionManager(application.applicationContext)

    // Отдельные троттлеры по 80 мс для шасси и дополнительных моторов
    private val chassisThrottler = CommandThrottler(intervalMs = 80L)
    private val auxThrottler = CommandThrottler(intervalMs = 80L)
    private val labThrottlers = mapOf(
        Ev3Port.A to CommandThrottler(intervalMs = 80L),
        Ev3Port.B to CommandThrottler(intervalMs = 80L),
        Ev3Port.C to CommandThrottler(intervalMs = 80L),
        Ev3Port.D to CommandThrottler(intervalMs = 80L)
    )

    private var raceSoundJob: Job? = null

    // ── Состояние Bluetooth-соединения ──
    val connectionState: StateFlow<ConnectionState> = btManager.connectionState

    // ── Список сопряжённых устройств и выбранное устройство ──
    private val _bondedDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val bondedDevices: StateFlow<List<BluetoothDevice>> = _bondedDevices.asStateFlow()

    private val _selectedDevice = MutableStateFlow<BluetoothDevice?>(null)
    val selectedDevice: StateFlow<BluetoothDevice?> = _selectedDevice.asStateFlow()

    // ── Активная вкладка режима управления ──
    private val _controlMode = MutableStateFlow(ControlMode.JOYSTICK_360)
    val controlMode: StateFlow<ControlMode> = _controlMode.asStateFlow()

    // ── Ограничитель скорости (10..100%) ──
    private val _speed = MutableStateFlow(SpeedPreset.NORMAL.speedPercent)
    val speed: StateFlow<Int> = _speed.asStateFlow()

    // ── Инверсия направления вращения для каждого порта (A, B, C, D) ──
    private val _invertPorts = MutableStateFlow(
        mapOf(
            Ev3Port.A to false,
            Ev3Port.B to false,
            Ev3Port.C to false,
            Ev3Port.D to false
        )
    )
    val invertPorts: StateFlow<Map<Ev3Port, Boolean>> = _invertPorts.asStateFlow()

    // ── Конфигурация портов шасси (по умолчанию B + C) ──
    private val _chassisConfig = MutableStateFlow(ChassisConfig.BC)
    val chassisConfig: StateFlow<ChassisConfig> = _chassisConfig.asStateFlow()

    // ── Режим торможения: true = Жёсткий тормоз (Brake), false = Плавный накат (Coast) ──
    private val _brakeMode = MutableStateFlow(true)
    val brakeMode: StateFlow<Boolean> = _brakeMode.asStateFlow()

    // ── Телеметрия текущей мощности левого и правого моторов шасси (-100..100) ──
    private val _leftMotorTelemetry = MutableStateFlow(0)
    val leftMotorTelemetry: StateFlow<Int> = _leftMotorTelemetry.asStateFlow()

    private val _rightMotorTelemetry = MutableStateFlow(0)
    val rightMotorTelemetry: StateFlow<Int> = _rightMotorTelemetry.asStateFlow()

    // ── Состояние ползунков в режиме «Лаборатория (4 мотора)» (-100..100) ──
    private val _labMotorPowers = MutableStateFlow(
        mapOf(
            Ev3Port.A to 0,
            Ev3Port.B to 0,
            Ev3Port.C to 0,
            Ev3Port.D to 0
        )
    )
    val labMotorPowers: StateFlow<Map<Ev3Port, Int>> = _labMotorPowers.asStateFlow()

    // ── Фиксация ползунков в Лаборатории (false = авто-возврат в 0 при отпускании, true = удержание) ──
    private val _labLatchMode = MutableStateFlow(false)
    val labLatchMode: StateFlow<Boolean> = _labLatchMode.asStateFlow()

    // ══════════════════════════════════════════════════════════════
    // Bluetooth подключение
    // ══════════════════════════════════════════════════════════════

    @SuppressLint("MissingPermission")
    fun refreshBondedDevices() {
        val list = btManager.getBondedDevices()
        _bondedDevices.value = list
        if (_selectedDevice.value == null && list.isNotEmpty()) {
            // Автоматически выбираем первый найденный блок EV3 или первое устройство в списке
            _selectedDevice.value = list.firstOrNull {
                (it.name ?: "").contains("EV3", ignoreCase = true)
            } ?: list.first()
        }
    }

    fun selectDevice(device: BluetoothDevice) {
        _selectedDevice.value = device
    }

    fun connect() {
        val device = _selectedDevice.value ?: return
        viewModelScope.launch {
            btManager.connect(device)
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            raceSoundJob?.cancel()
            resetTelemetry()
            btManager.sendImmediate(Ev3Protocol.motorStop(Ev3Protocol.PORTS_ALL, brake = true))
            btManager.disconnect()
            resetAllThrottlers()
        }
    }

    // ══════════════════════════════════════════════════════════════
    // Настройки и режимы
    // ══════════════════════════════════════════════════════════════

    fun setControlMode(mode: ControlMode) {
        if (_controlMode.value != mode) {
            _controlMode.value = mode
            // При смене вкладки безопасно останавливаем моторы
            stopAllMotorsWithCurrentBrake()
        }
    }

    fun setSpeed(value: Int) {
        _speed.value = value.coerceIn(10, 100)
    }

    fun selectSpeedPreset(preset: SpeedPreset) {
        _speed.value = preset.speedPercent
    }

    fun togglePortInversion(port: Ev3Port) {
        _invertPorts.update { current ->
            current.toMutableMap().apply {
                this[port] = !(current[port] ?: false)
            }
        }
    }

    fun setChassisConfig(config: ChassisConfig) {
        if (_chassisConfig.value != config) {
            stopChassis()
            _chassisConfig.value = config
        }
    }

    fun setBrakeMode(brake: Boolean) {
        _brakeMode.value = brake
    }

    fun setLabLatchMode(latch: Boolean) {
        _labLatchMode.value = latch
        if (!latch) {
            stopAllMotorsWithCurrentBrake()
        }
    }

    /**
     * Возвращает два дополнительных порта, которые не заняты шасси.
     * При стандартном шасси B + C возвращает Порт A и Порт D.
     */
    fun getAuxiliaryPorts(): Pair<Ev3Port, Ev3Port> {
        return when (_chassisConfig.value) {
            ChassisConfig.BC -> Ev3Port.A to Ev3Port.D
            ChassisConfig.AD -> Ev3Port.B to Ev3Port.C
            ChassisConfig.AB -> Ev3Port.C to Ev3Port.D
            ChassisConfig.CD -> Ev3Port.A to Ev3Port.B
        }
    }

    private fun applyInversion(port: Ev3Port, power: Int): Int {
        val inverted = _invertPorts.value[port] == true
        return if (inverted) -power else power
    }

    // ══════════════════════════════════════════════════════════════
    // Режим 1: Джойстик 360° (танковое / аркадное микширование)
    // ══════════════════════════════════════════════════════════════

    /**
     * Обработка смещения пальца на 360° джойстике.
     *
     * @param normalizedX координата X от -1.0 (влево) до +1.0 (вправо)
     * @param normalizedY координата Y от -1.0 (назад) до +1.0 (вперёд)
     */
    fun onJoystickMove(normalizedX: Float, normalizedY: Float) {
        val xPercent = (normalizedX.coerceIn(-1f, 1f) * 100f)
        val yPercent = (normalizedY.coerceIn(-1f, 1f) * 100f)
        val speedFactor = _speed.value / 100f

        // Формула танкового/аркадного микширования:
        // Left = clamp(Y + X, -100, 100) * SpeedLimit
        // Right = clamp(Y - X, -100, 100) * SpeedLimit
        val rawLeft = ((yPercent + xPercent).coerceIn(-100f, 100f) * speedFactor).roundToInt()
        val rawRight = ((yPercent - xPercent).coerceIn(-100f, 100f) * speedFactor).roundToInt()

        _leftMotorTelemetry.value = rawLeft
        _rightMotorTelemetry.value = rawRight

        val config = _chassisConfig.value
        val finalLeft = applyInversion(config.leftPort, rawLeft)
        val finalRight = applyInversion(config.rightPort, rawRight)

        viewModelScope.launch {
            chassisThrottler.trySend {
                val leftPacket = Ev3Protocol.motorPowerStart(config.leftPort.mask, finalLeft)
                val rightPacket = Ev3Protocol.motorPowerStart(config.rightPort.mask, finalRight)
                btManager.sendCommand(leftPacket, rightPacket)
            }
        }
    }

    /**
     * Мгновенная остановка шасси при отпускании джойстика или кнопок D-Pad (обходит троттлер).
     */
    fun stopChassis() {
        _leftMotorTelemetry.value = 0
        _rightMotorTelemetry.value = 0
        chassisThrottler.reset()
        val config = _chassisConfig.value
        val useBrake = _brakeMode.value
        viewModelScope.launch {
            btManager.sendImmediate(
                Ev3Protocol.motorStop(config.combinedMask, brake = useBrake)
            )
        }
    }

    // ══════════════════════════════════════════════════════════════
    // Режим 2: Крестовина (D-Pad) для шасси
    // ══════════════════════════════════════════════════════════════

    fun moveForward() {
        driveChassisTank(leftRaw = _speed.value, rightRaw = _speed.value)
    }

    fun moveBackward() {
        driveChassisTank(leftRaw = -_speed.value, rightRaw = -_speed.value)
    }

    fun turnLeft() {
        driveChassisTank(leftRaw = -_speed.value, rightRaw = _speed.value)
    }

    fun turnRight() {
        driveChassisTank(leftRaw = _speed.value, rightRaw = -_speed.value)
    }

    private fun driveChassisTank(leftRaw: Int, rightRaw: Int) {
        _leftMotorTelemetry.value = leftRaw
        _rightMotorTelemetry.value = rightRaw

        val config = _chassisConfig.value
        val finalLeft = applyInversion(config.leftPort, leftRaw)
        val finalRight = applyInversion(config.rightPort, rightRaw)

        viewModelScope.launch {
            chassisThrottler.trySend {
                val leftPacket = Ev3Protocol.motorPowerStart(config.leftPort.mask, finalLeft)
                val rightPacket = Ev3Protocol.motorPowerStart(config.rightPort.mask, finalRight)
                btManager.sendCommand(leftPacket, rightPacket)
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    // Управление дополнительными механизмами (Порт A и Порт D / доп. порты)
    // ══════════════════════════════════════════════════════════════

    /**
     * Вращение указанного порта при удержании кнопки (с учётом SpeedLimit и инверсии порта).
     */
    fun drivePortHold(port: Ev3Port, forward: Boolean) {
        val basePower = if (forward) _speed.value else -_speed.value
        val finalPower = applyInversion(port, basePower)
        viewModelScope.launch {
            auxThrottler.trySend {
                btManager.sendCommand(Ev3Protocol.motorPowerStart(port.mask, finalPower))
            }
        }
    }

    /**
     * Мгновенная остановка одного порта при отпускании кнопки (обходит троттлер).
     */
    fun stopPort(port: Ev3Port) {
        auxThrottler.reset()
        labThrottlers[port]?.reset()
        _labMotorPowers.update { current ->
            current.toMutableMap().apply { this[port] = 0 }
        }
        val useBrake = _brakeMode.value
        viewModelScope.launch {
            btManager.sendImmediate(
                Ev3Protocol.motorStop(port.mask, brake = useBrake)
            )
        }
    }

    // ══════════════════════════════════════════════════════════════
    // Режим 3: Лаборатория (4 независимых мотора A, B, C, D)
    // ══════════════════════════════════════════════════════════════

    /**
     * Изменение мощности отдельного мотора на слайдере в режиме Лаборатории (-100%..+100%).
     */
    fun setLabMotorPower(port: Ev3Port, rawPower: Int) {
        val clamped = rawPower.coerceIn(-100, 100)
        _labMotorPowers.update { current ->
            current.toMutableMap().apply { this[port] = clamped }
        }

        if (clamped == 0) {
            stopPort(port)
            return
        }

        val finalPower = applyInversion(port, clamped)
        val throttler = labThrottlers[port] ?: auxThrottler
        viewModelScope.launch {
            throttler.trySend {
                btManager.sendCommand(Ev3Protocol.motorPowerStart(port.mask, finalPower))
            }
        }
    }

    /**
     * Вызывается при отпускании ползунка в режиме Лаборатории.
     * Если не включён режим удержания (Круиз), ползунок плавно сбрасывается в 0 и останавливает мотор.
     */
    fun onLabSliderRelease(port: Ev3Port) {
        if (!_labLatchMode.value) {
            stopPort(port)
        }
    }

    // ══════════════════════════════════════════════════════════════
    // Звуковая панель блока EV3
    // ══════════════════════════════════════════════════════════════

    /**
     * Тест связи (Писк 1000 Гц, 300 мс).
     */
    fun beep() {
        viewModelScope.launch {
            btManager.sendCommand(Ev3Protocol.beep())
        }
    }

    /**
     * 🏁 Сигнал старта гонки — мелодия обратного отсчёта из трёх тонов через opSOUND (0x94):
     * 2 коротких сигнала подготовки (523 Гц, 200 мс) и 1 высокий стартовый сигнал (1046 Гц, 550 мс).
     */
    fun playRaceStartSignal() {
        raceSoundJob?.cancel()
        raceSoundJob = viewModelScope.launch {
            // Тон 1: Внимание (523 Гц — До первой октавы)
            btManager.sendCommand(Ev3Protocol.playTone(frequencyHz = 523, durationMs = 200))
            delay(450L)
            // Тон 2: Приготовиться (659 Гц — Ми первой октавы)
            btManager.sendCommand(Ev3Protocol.playTone(frequencyHz = 659, durationMs = 200))
            delay(450L)
            // Тон 3: СТАРТ! (1046 Гц — До второй октавы, длинный)
            btManager.sendCommand(Ev3Protocol.playTone(frequencyHz = 1046, durationMs = 550))
        }
    }

    // ══════════════════════════════════════════════════════════════
    // Экстренное торможение (СТОП ВСЁ)
    // ══════════════════════════════════════════════════════════════

    /**
     * Мгновенная остановка ВСЕХ 4 портов (маска 0x0F) с жёстким тормозом (Brake = true).
     * Полностью обходит троттлер.
     */
    fun emergencyStop() {
        raceSoundJob?.cancel()
        resetTelemetry()
        resetAllThrottlers()
        viewModelScope.launch {
            btManager.sendImmediate(
                Ev3Protocol.motorStop(Ev3Protocol.PORTS_ALL, brake = true)
            )
        }
    }

    private fun stopAllMotorsWithCurrentBrake() {
        resetTelemetry()
        resetAllThrottlers()
        val useBrake = _brakeMode.value
        viewModelScope.launch {
            btManager.sendImmediate(
                Ev3Protocol.motorStop(Ev3Protocol.PORTS_ALL, brake = useBrake)
            )
        }
    }

    private fun resetTelemetry() {
        _leftMotorTelemetry.value = 0
        _rightMotorTelemetry.value = 0
        _labMotorPowers.value = mapOf(
            Ev3Port.A to 0,
            Ev3Port.B to 0,
            Ev3Port.C to 0,
            Ev3Port.D to 0
        )
    }

    private fun resetAllThrottlers() {
        chassisThrottler.reset()
        auxThrottler.reset()
        labThrottlers.values.forEach { it.reset() }
    }

    override fun onCleared() {
        super.onCleared()
        raceSoundJob?.cancel()
        viewModelScope.launch {
            try {
                btManager.sendImmediate(Ev3Protocol.motorStop(Ev3Protocol.PORTS_ALL, brake = true))
            } catch (_: Exception) {}
            btManager.disconnect()
        }
    }
}
