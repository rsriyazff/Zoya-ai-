package com.example.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Base64
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AudioUtils(private val context: Context) {
    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var _cachedPcmData = byteArrayOf()

    // Gemini recommends 16kHz for input audio
    private val sampleRateInput = 16000
    private val channelConfigInput = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT

    private var audioTrack: AudioTrack? = null

    fun startRecording() {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        try {
            val bufferSize = AudioRecord.getMinBufferSize(sampleRateInput, channelConfigInput, audioFormat)
            if (bufferSize <= 0) return

            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRateInput,
                channelConfigInput,
                audioFormat,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                return
            }

            audioRecord?.startRecording()
            isRecording = true

            Thread {
                try {
                    val audioBuffer = ByteArray(bufferSize)
                    val outputStream = ByteArrayOutputStream()
                    while (isRecording) {
                        val read = audioRecord?.read(audioBuffer, 0, audioBuffer.size) ?: -1
                        if (read > 0) {
                            outputStream.write(audioBuffer, 0, read)
                        } else if (read == 0 || read == -1) {
                            Thread.sleep(5) // Prevent tight loop if empty or null
                        }
                    }
                    _cachedPcmData = outputStream.toByteArray()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }.start()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun stopRecordingAndGetBase64Wav(): String? {
        isRecording = false
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null

        Thread.sleep(100) // Small delay to ensure stream is flushed

        if (_cachedPcmData.isEmpty()) return null
        
        val wavData = addWavHeader(_cachedPcmData, sampleRateInput)
        return Base64.encodeToString(wavData, Base64.NO_WRAP)
    }

    suspend fun playAudioFromBase64(base64Pcm: String) = withContext(Dispatchers.IO) {
        try {
            val decodedData = Base64.decode(base64Pcm, Base64.DEFAULT)
            // Gemini API returns 24kHz PCM for output audio
            val sampleRateOutput = 24000
            val bufferSize = AudioTrack.getMinBufferSize(
                sampleRateOutput,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            
            if (bufferSize <= 0) return@withContext

            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build())
                .setAudioFormat(AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRateOutput)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build())
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            if (audioTrack?.state != AudioTrack.STATE_INITIALIZED) {
                return@withContext
            }

            audioTrack?.play()
            
            // Depending on if the return is raw PCM or RIFF WAV, we might need to skip header.
            // Let's assume it's raw PCM 24khz from inlineData.
            var dataToPlay = decodedData
            if(isWavFormat(decodedData)) {
                dataToPlay = decodedData.copyOfRange(44, decodedData.size)
            }

            audioTrack?.write(dataToPlay, 0, dataToPlay.size)
            
            // Wait until playback is done
            val durationMs = (dataToPlay.size.toFloat() / (sampleRateOutput * 2)) * 1000
            kotlinx.coroutines.delay(durationMs.toLong() + 200)

            audioTrack?.stop()
            audioTrack?.release()
            audioTrack = null

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    fun stopPlayback() {
        try {
            audioTrack?.stop()
            audioTrack?.release()
            audioTrack = null
        } catch (e: Exception) {}
    }

    private fun isWavFormat(data: ByteArray): Boolean {
        if (data.size < 44) return false
        val riff = String(data, 0, 4)
        val wave = String(data, 8, 4)
        return riff == "RIFF" && wave == "WAVE"
    }

    private fun addWavHeader(pcmData: ByteArray, sampleRate: Int): ByteArray {
        val totalDataLen = pcmData.size + 36
        val byteRate = sampleRate * 2 // 16-bit mono
        val header = ByteArray(44)
        header[0] = 'R'.code.toByte(); header[1] = 'I'.code.toByte(); header[2] = 'F'.code.toByte(); header[3] = 'F'.code.toByte()
        header[4] = (totalDataLen and 0xff).toByte()
        header[5] = ((totalDataLen shr 8) and 0xff).toByte()
        header[6] = ((totalDataLen shr 16) and 0xff).toByte()
        header[7] = ((totalDataLen shr 24) and 0xff).toByte()
        header[8] = 'W'.code.toByte(); header[9] = 'A'.code.toByte(); header[10] = 'V'.code.toByte(); header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte(); header[13] = 'm'.code.toByte(); header[14] = 't'.code.toByte(); header[15] = ' '.code.toByte()
        header[16] = 16; header[17] = 0; header[18] = 0; header[19] = 0 // Subchunk1Size
        header[20] = 1; header[21] = 0 // AudioFormat (PCM)
        header[22] = 1; header[23] = 0 // NumChannels (Mono)
        header[24] = (sampleRate and 0xff).toByte()
        header[25] = ((sampleRate shr 8) and 0xff).toByte()
        header[26] = ((sampleRate shr 16) and 0xff).toByte()
        header[27] = ((sampleRate shr 24) and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte()
        header[29] = ((byteRate shr 8) and 0xff).toByte()
        header[30] = ((byteRate shr 16) and 0xff).toByte()
        header[31] = ((byteRate shr 24) and 0xff).toByte()
        header[32] = 2; header[33] = 0 // BlockAlign
        header[34] = 16; header[35] = 0 // BitsPerSample
        header[36] = 'd'.code.toByte(); header[37] = 'a'.code.toByte(); header[38] = 't'.code.toByte(); header[39] = 'a'.code.toByte()
        header[40] = (pcmData.size and 0xff).toByte()
        header[41] = ((pcmData.size shr 8) and 0xff).toByte()
        header[42] = ((pcmData.size shr 16) and 0xff).toByte()
        header[43] = ((pcmData.size shr 24) and 0xff).toByte()

        return header + pcmData
    }
}
