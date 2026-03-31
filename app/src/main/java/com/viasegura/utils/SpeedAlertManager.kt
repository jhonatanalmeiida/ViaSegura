package com.viasegura.utils

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.ToneGenerator
import android.media.AudioManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Gerencia os alertas sonoros e de vibração quando o usuário excede o limite de velocidade.
 */
class SpeedAlertManager(private val context: Context) {

    private var toneGenerator: ToneGenerator? = null
    private var alertaAtivo = false

    /**
     * Emite alerta sonoro e vibração indicando excesso de velocidade.
     * Chamado quando a velocidade atual supera o limite configurado.
     */
    fun emitirAlerta() {
        if (alertaAtivo) return // Evita alertas repetidos em cascata
        alertaAtivo = true

        // Alerta sonoro usando ToneGenerator (não requer arquivo de áudio externo)
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, 100)
            toneGenerator?.startTone(ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK, 1000)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Vibração padrão: 3 pulsos curtos
        vibrar(longArrayOf(0, 300, 150, 300, 150, 300))
    }

    /**
     * Cancela o estado de alerta, permitindo que novos alertas sejam emitidos.
     * Chamado quando a velocidade volta abaixo do limite.
     */
    fun cancelarAlerta() {
        alertaAtivo = false
        toneGenerator?.stopTone()
        toneGenerator?.release()
        toneGenerator = null
    }

    /**
     * Executa vibração com o padrão fornecido.
     * Compatível com Android 8+ (API 26+).
     */
    private fun vibrar(padrao: LongArray) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager =
                context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            val vibrator = vibratorManager.defaultVibrator
            vibrator.vibrate(VibrationEffect.createWaveform(padrao, -1))
        } else {
            @Suppress("DEPRECATION")
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(padrao, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(padrao, -1)
            }
        }
    }

    /**
     * Libera todos os recursos ao destruir a Activity/Service.
     */
    fun release() {
        toneGenerator?.release()
        toneGenerator = null
    }
}
