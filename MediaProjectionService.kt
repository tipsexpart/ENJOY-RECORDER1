package com.enjoy.recorder.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Binder
import android.os.Build
import android.os.Environment
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.enjoy.recorder.MainActivity
import com.enjoy.recorder.R
import com.enjoy.recorder.recorder.RegionCropRenderer
import java.io.File
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

class MediaProjectionService : Service() {

    private val binder = LocalBinder()
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var videoEncoder: MediaCodec? = null
    private var audioRecord: AudioRecord? = null
    private var mediaMuxer: MediaMuxer? = null

    private var videoTrackIndex = -1
    private var audioTrackIndex = -1
    private var isMuxerStarted = false
    private val isRecording = AtomicBoolean(false)
    private var isPaused = AtomicBoolean(false)

    private var cropRegion: Rect? = null
    private var regionCropRenderer: RegionCropRenderer? = null
    private var outputFile: File? = null

    inner class LocalBinder : Binder() {
        fun getService(): MediaProjectionService = this@MediaProjectionService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        when (action) {
            ACTION_START -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
                val resultData: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_RESULT_DATA)
                }
                val width = intent.getIntExtra(EXTRA_WIDTH, 1920)
                val height = intent.getIntExtra(EXTRA_HEIGHT, 1080)
                val dpi = intent.getIntExtra(EXTRA_DPI, 400)
                val fps = intent.getIntExtra(EXTRA_FPS, 60)
                val bitrate = intent.getIntExtra(EXTRA_BITRATE, 12_000_000)
                val cropLeft = intent.getIntExtra(EXTRA_CROP_LEFT, -1)
                val cropTop = intent.getIntExtra(EXTRA_CROP_TOP, -1)
                val cropWidth = intent.getIntExtra(EXTRA_CROP_WIDTH, -1)
                val cropHeight = intent.getIntExtra(EXTRA_CROP_HEIGHT, -1)

                if (cropWidth > 0 && cropHeight > 0) {
                    cropRegion = Rect(cropLeft, cropTop, cropLeft + cropWidth, cropTop + cropHeight)
                } else {
                    cropRegion = null
                }

                startForegroundServiceNotification()
                if (resultData != null) {
                    initMediaProjection(resultCode, resultData)
                    startRecordingPipeline(width, height, dpi, fps, bitrate)
                }
            }
            ACTION_PAUSE -> isPaused.set(true)
            ACTION_RESUME -> isPaused.set(false)
            ACTION_STOP -> stopRecordingPipeline()
        }
        return START_STICKY
    }

    private fun startForegroundServiceNotification() {
        val stopIntent = Intent(this, MediaProjectionService::class.java).apply { action = ACTION_STOP }
        val stopPendingIntent = PendingIntent.getService(this, 1, stopIntent, PendingIntent.FLAG_IMMUTABLE)

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ENJOY RECORDER")
            .setContentText("Screen Recording Active")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_media_pause, "Stop", stopPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun initMediaProjection(resultCode: Int, data: Intent) {
        val mpManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = mpManager.getMediaProjection(resultCode, data)
        mediaProjection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                stopRecordingPipeline()
            }
        }, null)
    }

    private fun startRecordingPipeline(width: Int, height: Int, dpi: Int, fps: Int, bitrate: Int) {
        try {
            val recordDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "ENJOY Recorder")
            if (!recordDir.exists()) recordDir.mkdirs()
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            outputFile = File(recordDir, "EnjoyRecord_$timeStamp.mp4")

            mediaMuxer = MediaMuxer(outputFile!!.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            // Setup Hardware Video Encoder (H.264 / AVC)
            val recordWidth = cropRegion?.width() ?: width
            val recordHeight = cropRegion?.height() ?: height

            val videoFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, recordWidth, recordHeight).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
                setInteger(MediaFormat.KEY_FRAME_RATE, fps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1) // 1s keyframe
            }

            videoEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            videoEncoder?.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val inputSurface = videoEncoder?.createInputSurface()
            videoEncoder?.start()

            // If Region recording is requested, setup OpenGL Cropping Pipeline
            if (cropRegion != null && inputSurface != null) {
                regionCropRenderer = RegionCropRenderer(width, height, cropRegion!!, inputSurface)
                virtualDisplay = mediaProjection?.createVirtualDisplay(
                    "EnjoyRecorderVirtualDisplay",
                    width, height, dpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    regionCropRenderer?.inputSurface,
                    null, null
                )
            } else {
                virtualDisplay = mediaProjection?.createVirtualDisplay(
                    "EnjoyRecorderVirtualDisplay",
                    recordWidth, recordHeight, dpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    inputSurface,
                    null, null
                )
            }

            isRecording.set(true)
            startEncoderLoop()
        } catch (e: Exception) {
            e.printStackTrace()
            stopRecordingPipeline()
        }
    }

    private fun startEncoderLoop() {
        Thread {
            val bufferInfo = MediaCodec.BufferInfo()
            while (isRecording.get()) {
                if (isPaused.get()) {
                    Thread.sleep(50)
                    continue
                }
                val encoder = videoEncoder ?: break
                val outputBufferIndex = encoder.dequeueOutputBuffer(bufferInfo, 10_000)
                if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    if (!isMuxerStarted) {
                        videoTrackIndex = mediaMuxer?.addTrack(encoder.outputFormat) ?: -1
                        mediaMuxer?.start()
                        isMuxerStarted = true
                    }
                } else if (outputBufferIndex >= 0) {
                    val encodedData = encoder.getOutputBuffer(outputBufferIndex)
                    if (encodedData != null && isMuxerStarted && bufferInfo.size > 0) {
                        encodedData.position(bufferInfo.offset)
                        encodedData.limit(bufferInfo.offset + bufferInfo.size)
                        mediaMuxer?.writeSampleData(videoTrackIndex, encodedData, bufferInfo)
                    }
                    encoder.releaseOutputBuffer(outputBufferIndex, false)
                }
            }
        }.start()
    }

    private fun stopRecordingPipeline() {
        if (!isRecording.getAndSet(false)) return
        try {
            virtualDisplay?.release()
            regionCropRenderer?.release()
            videoEncoder?.stop()
            videoEncoder?.release()
            audioRecord?.stop()
            audioRecord?.release()
            if (isMuxerStarted) {
                mediaMuxer?.stop()
                mediaMuxer?.release()
            }
            mediaProjection?.stop()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Screen Recorder Service",
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "Foreground service notification for ENJOY RECORDER" }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID = "enjoy_recorder_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "ACTION_START"
        const val ACTION_PAUSE = "ACTION_PAUSE"
        const val ACTION_RESUME = "ACTION_RESUME"
        const val ACTION_STOP = "ACTION_STOP"
        const val EXTRA_RESULT_CODE = "EXTRA_RESULT_CODE"
        const val EXTRA_RESULT_DATA = "EXTRA_RESULT_DATA"
        const val EXTRA_WIDTH = "EXTRA_WIDTH"
        const val EXTRA_HEIGHT = "EXTRA_HEIGHT"
        const val EXTRA_DPI = "EXTRA_DPI"
        const val EXTRA_FPS = "EXTRA_FPS"
        const val EXTRA_BITRATE = "EXTRA_BITRATE"
        const val EXTRA_CROP_LEFT = "EXTRA_CROP_LEFT"
        const val EXTRA_CROP_TOP = "EXTRA_CROP_TOP"
        const val EXTRA_CROP_WIDTH = "EXTRA_CROP_WIDTH"
        const val EXTRA_CROP_HEIGHT = "EXTRA_CROP_HEIGHT"
    }
}
