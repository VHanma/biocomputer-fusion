package com.vhanma.kialink

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.sin

class DreamGateEngine(private val context: Context, private val store: MetamorphStore) {
    fun arm(config: DreamGateConfig): List<Long> {
        disarm()
        store.setDreamMission(config.mission)
        val now = System.currentTimeMillis()
        val times = mutableListOf<Long>()
        repeat(config.cueCount.coerceIn(1, 8)) { index ->
            val triggerAt = now + (config.cueDelayMinutes + index * config.spacingMinutes) * 60_000L
            times += triggerAt
            scheduleOne(index, triggerAt, config)
        }
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        store.appendLog("${fmt.format(Date())} | DREAMGATE ARMED: ${times.joinToString { fmt.format(Date(it)) }} | mission=${config.mission}")
        return times
    }

    fun disarm() {
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        repeat(8) { index ->
            existingPending(index)?.let { alarm.cancel(it) }
        }
    }

    fun testCue(volumePercent: Int = 18, vibrate: Boolean = true) {
        Thread { DreamCuePlayer.play(context.applicationContext, volumePercent, vibrate) }.start()
    }

    private fun scheduleOne(index: Int, triggerAt: Long, config: DreamGateConfig) {
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pending(index, config)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarm.canScheduleExactAlarms()) {
                    alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                } else {
                    alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                }
            } else {
                alarm.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            }
        } catch (_: SecurityException) {
            alarm.set(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }

    private fun pending(index: Int, config: DreamGateConfig): PendingIntent {
        val intent = Intent(context, DreamGateReceiver::class.java).apply {
            putExtra("volume", config.volumePercent)
            putExtra("vibrate", config.vibrationEnabled)
            putExtra("mission", config.mission)
            putExtra("cue_index", index)
        }
        return PendingIntent.getBroadcast(
            context,
            5400 + index,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun existingPending(index: Int): PendingIntent? {
        val intent = Intent(context, DreamGateReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            5400 + index,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

class DreamGateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val volume = intent.getIntExtra("volume", 18)
        val vibrate = intent.getBooleanExtra("vibrate", true)
        val mission = intent.getStringExtra("mission") ?: "Become lucid and remember the mission."
        val index = intent.getIntExtra("cue_index", 0)
        Thread {
            try {
                DreamCuePlayer.play(context.applicationContext, volume, vibrate)
                val store = MetamorphStore(context.applicationContext)
                val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
                store.appendLog("${fmt.format(Date())} | DREAMGATE CUE ${index + 1} FIRED | $mission")
            } finally {
                pending.finish()
            }
        }.start()
    }
}

object DreamCuePlayer {
    fun play(context: Context, volumePercent: Int, vibrate: Boolean) {
        if (vibrate) vibrate(context)
        val sampleRate = 44_100
        val frequencies = doubleArrayOf(400.0, 600.0, 800.0)
        val toneSeconds = 0.72
        val gapSeconds = 0.16
        val totalSamples = ((toneSeconds * frequencies.size + gapSeconds * (frequencies.size - 1)) * sampleRate).toInt()
        val pcm = ShortArray(totalSamples)
        var cursor = 0
        val amplitude = (Short.MAX_VALUE * (volumePercent.coerceIn(1, 80) / 100.0)).toInt()

        frequencies.forEachIndexed { idx, frequency ->
            val samples = (toneSeconds * sampleRate).toInt()
            repeat(samples) { n ->
                val edge = (0.08 * sampleRate).toInt().coerceAtLeast(1)
                val fade = when {
                    n < edge -> n.toDouble() / edge
                    n > samples - edge -> (samples - n).toDouble() / edge
                    else -> 1.0
                }.coerceIn(0.0, 1.0)
                val value = sin(2.0 * PI * frequency * n / sampleRate)
                if (cursor < pcm.size) pcm[cursor++] = (value * amplitude * fade).toInt().toShort()
            }
            if (idx < frequencies.lastIndex) {
                repeat((gapSeconds * sampleRate).toInt()) {
                    if (cursor < pcm.size) pcm[cursor++] = 0
                }
            }
        }

        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(sampleRate)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()
        val track = AudioTrack.Builder()
            .setAudioAttributes(attrs)
            .setAudioFormat(format)
            .setBufferSizeInBytes(pcm.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        track.write(pcm, 0, pcm.size)
        track.play()
        val durationMs = (pcm.size * 1000L / sampleRate) + 150L
        Thread.sleep(durationMs)
        runCatching { track.stop() }
        track.release()
    }

    private fun vibrate(context: Context) {
        val pattern = longArrayOf(0, 90, 140, 90, 140, 140)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            manager.defaultVibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } else {
            @Suppress("DEPRECATION")
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(pattern, -1)
            }
        }
    }
}
