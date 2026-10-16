package com.ev3controller.bluetooth

import android.os.SystemClock
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Троттлер команд — ограничивает отправку команд движения от джойстика и ползунков
 * не чаще одного раза в [intervalMs] миллисекунд (по умолчанию 80 мс),
 * чтобы не переполнять входной буфер Bluetooth модуля LEGO EV3.
 *
 * Команды STOP и экстренного торможения обходят троттлер и отправляются мгновенно.
 */
class CommandThrottler(private val intervalMs: Long = 80L) {

    private var lastSentTime = 0L
    private val mutex = Mutex()

    /**
     * Выполняет [sendAction], если с момента предыдущей отправки прошло не менее [intervalMs] мс.
     * Использует монотонные часы [SystemClock.elapsedRealtime] для защиты от скачков системного времени.
     *
     * @return true, если команда была отправлена, false — если пропущена ограничителем
     */
    suspend fun trySend(sendAction: suspend () -> Unit): Boolean {
        mutex.withLock {
            val now = SystemClock.elapsedRealtime()
            if (now - lastSentTime >= intervalMs) {
                lastSentTime = now
                sendAction()
                return true
            }
            return false
        }
    }

    /**
     * Сброс таймера троттлера (вызывается при остановке моторов или отключении),
     * чтобы следующее нажатие сработало мгновенно без ожидания.
     */
    fun reset() {
        lastSentTime = 0L
    }
}
