package com.ev3controller.bluetooth

/**
 * Перечисление портов моторов блока LEGO Mindstorms EV3.
 */
enum class Ev3Port(val mask: Int, val label: String) {
    A(Ev3Protocol.PORT_A, "A"),
    B(Ev3Protocol.PORT_B, "B"),
    C(Ev3Protocol.PORT_C, "C"),
    D(Ev3Protocol.PORT_D, "D")
}

/**
 * Конфигурация портов шасси (левое и правое колесо).
 */
enum class ChassisConfig(
    val title: String,
    val shortLabel: String,
    val leftPort: Ev3Port,
    val rightPort: Ev3Port
) {
    BC("Порты B + C (Стандарт)", "B + C", Ev3Port.B, Ev3Port.C),
    AD("Порты A + D (Крайние)", "A + D", Ev3Port.A, Ev3Port.D),
    AB("Порты A + B (Слева)", "A + B", Ev3Port.A, Ev3Port.B),
    CD("Порты C + D (Справа)", "C + D", Ev3Port.C, Ev3Port.D);

    val combinedMask: Int get() = leftPort.mask or rightPort.mask
}

/**
 * Конструктор бинарных пакетов протокола LEGO Mindstorms EV3 Direct Command.
 *
 * Все команды используют DIRECT_COMMAND_NO_REPLY (0x80) в формате Little-Endian
 * для минимальной задержки по Bluetooth Classic (SPP).
 */
object Ev3Protocol {

    // ── Битовые маски портов моторов ──────────────────────────────
    const val PORT_A = 0x01
    const val PORT_B = 0x02
    const val PORT_C = 0x04
    const val PORT_D = 0x08
    const val PORTS_BC = 0x06  // B + C (шасси по умолчанию)
    const val PORTS_AD = 0x09  // A + D
    const val PORTS_AB = 0x03  // A + B
    const val PORTS_ALL = 0x0F // A + B + C + D (все моторы)

    /**
     * Воспроизведение звукового тона на блоке EV3.
     * opSOUND = 0x94, CMD_TONE = 0x01, громкость = 50% (0x32).
     *
     * @param frequencyHz частота звука в Гц (например, 440..2000)
     * @param durationMs длительность звучания в мс
     */
    fun playTone(frequencyHz: Int, durationMs: Int): ByteArray {
        val freq = frequencyHz.coerceIn(250, 10000)
        val dur = durationMs.coerceIn(20, 5000)
        val freqLsb = (freq and 0xFF).toByte()
        val freqMsb = ((freq shr 8) and 0xFF).toByte()
        val durLsb = (dur and 0xFF).toByte()
        val durMsb = ((dur shr 8) and 0xFF).toByte()

        return byteArrayOf(
            0x0F, 0x00,                         // длина пакета = 15 байт (LE)
            0x00, 0x00,                         // счётчик сообщений = 0
            0x80.toByte(), 0x00, 0x00,          // DIRECT_COMMAND_NO_REPLY, 0 переменных
            0x94.toByte(), 0x01,                // opSOUND (0x94), TONE (0x01)
            0x81.toByte(), 0x32,                // LC1 громкость = 50 (0x32)
            0x82.toByte(), freqLsb, freqMsb,    // LC2 частота (LE)
            0x82.toByte(), durLsb, durMsb       // LC2 длительность в мс (LE)
        )
    }

    /**
     * Команда тестового сигнала связи (писк 1000 Гц, 300 мс).
     */
    fun beep(): ByteArray = playTone(frequencyHz = 1000, durationMs = 300)

    /**
     * Установка мощности мотора и немедленный старт.
     * opOUTPUT_POWER (0xA4) + opOUTPUT_START (0xA6).
     *
     * @param portMask битовая маска портов (PORT_A, PORT_B, PORT_C, PORT_D или их комбинация)
     * @param power мощность от -100 до 100
     */
    fun motorPowerStart(portMask: Int, power: Int): ByteArray {
        val clampedPower = power.coerceIn(-100, 100)
        return byteArrayOf(
            0x0D, 0x00,                         // длина пакета = 13 байт (LE)
            0x00, 0x00,                         // счётчик сообщений = 0
            0x80.toByte(), 0x00, 0x00,          // DIRECT_COMMAND_NO_REPLY
            0xA4.toByte(),                      // opOUTPUT_POWER (0xA4)
            0x00,                               // Layer 0 (главный блок)
            portMask.toByte(),                  // битовая маска портов
            0x81.toByte(), clampedPower.toByte(), // LC1 мощность (-100..100 в доп. коде)
            0xA6.toByte(),                      // opOUTPUT_START (0xA6)
            0x00,                               // Layer 0
            portMask.toByte()                   // битовая маска портов
        )
    }

    /**
     * Остановка моторов.
     * opOUTPUT_STOP (0xA3).
     *
     * @param portMask битовая маска портов (например, PORTS_ALL = 0x0F)
     * @param brake true = жёсткий тормоз (Brake = 0x01), false = плавный накат (Coast = 0x00)
     */
    fun motorStop(portMask: Int, brake: Boolean = true): ByteArray {
        return byteArrayOf(
            0x09, 0x00,                         // длина пакета = 9 байт (LE)
            0x00, 0x00,                         // счётчик сообщений = 0
            0x80.toByte(), 0x00, 0x00,          // DIRECT_COMMAND_NO_REPLY
            0xA3.toByte(),                      // opOUTPUT_STOP (0xA3)
            0x00,                               // Layer 0
            portMask.toByte(),                  // битовая маска портов
            if (brake) 0x01 else 0x00           // 0x01 = Brake (жёсткий), 0x00 = Coast (накат)
        )
    }
}
