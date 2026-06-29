package com.chatmind.chat_mind

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.DisplayMetrics
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.embedding.engine.FlutterEngineCache
import io.flutter.plugin.common.MethodChannel
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

class MainActivity : FlutterActivity() {
    private val TAG = "ChatMind"

    private val PERMISSION_CHANNEL = "com.chatmind/permissions"
    private val SCREENSHOT_CHANNEL = "com.chatmind/screenshot"
    private val FLOAT_WINDOW_CHANNEL = "com.chatmind/floatwindow"

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var selectionOverlay: FrameLayout? = null
    private var windowManager: WindowManager? = null

    private var selectionRect = android.graphics.Rect()

    private val SCREEN_CAPTURE_REQUEST = 1001
    private var pendingScreenshotResult: MethodChannel.Result? = null
    private var pendingCaptureArea: android.graphics.Rect? = null

    private var screenshotEngine: FlutterEngine? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppContext.context = this
        // 处理从悬浮窗传来的截图请求
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == "START_SCREENSHOT") {
            Handler(Looper.getMainLooper()).postDelayed({
                showSelectionOverlay()
            }, 500)
        } else if (intent?.action == "CAPTURE_WITH_AREA") {
            val left = intent.getIntExtra("left", 0)
            val top = intent.getIntExtra("top", 0)
            val right = intent.getIntExtra("right", 0)
            val bottom = intent.getIntExtra("bottom", 0)
            pendingCaptureArea = android.graphics.Rect(left, top, right, bottom)
            startMediaProjection()
        }
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        FlutterEngineCache.getInstance().put("chat_mind_engine", flutterEngine)
        screenshotEngine = flutterEngine
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, PERMISSION_CHANNEL)
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "checkOverlayPermission" -> result.success(Settings.canDrawOverlays(this))
                    "requestOverlayPermission" -> {
                        startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                        result.success(true)
                    }
                    else -> result.notImplemented()
                }
            }

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, SCREENSHOT_CHANNEL)
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "showSelectionOverlay" -> {
                        val intent = Intent(this, FloatWindowService::class.java)
                        intent.action = "START_SCREENSHOT"
                        startService(intent)
                        result.success(true)
                    }
                    "hideSelectionOverlay" -> {
                        result.success(true)
                    }
                    "captureScreenshot" -> {
                        result.success(true)
                    }
                    "captureArea" -> {
                        result.success(true)
                    }
                    else -> result.notImplemented()
                }
            }

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, FLOAT_WINDOW_CHANNEL)
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "showFloatBall" -> {
                        try {
                            val captureIntent = Intent(this, CaptureActivity::class.java)
                            captureIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            startActivity(captureIntent)
                            result.success(true)
                        } catch (e: Exception) {
                            result.success(false)
                        }
                    }
                    "hideFloatBall" -> {
                        try {
                            stopService(Intent(this, FloatWindowService::class.java))
                            result.success(true)
                        } catch (e: Exception) {
                            result.success(false)
                        }
                    }
                    "showAnalysisPanel" -> {
                        val psychology = call.argument<String>("psychology") ?: ""
                        val intention = call.argument<String>("intention") ?: ""
                        val riskWarning = call.argument<String>("riskWarning")
                        val scene = call.argument<String>("scene")
                        @Suppress("UNCHECKED_CAST")
                        val replies = call.argument<List<Map<String, String>>>("replies") ?: listOf()

                        val intent = Intent(this, FloatWindowService::class.java)
                        intent.action = "UPDATE_ANALYSIS"
                        if (scene != null && scene.isNotEmpty()) {
                            intent.putExtra("scene", scene)
                        }
                        intent.putExtra("psychology", psychology)
                        intent.putExtra("intention", intention)
                        intent.putExtra("riskWarning", riskWarning)
                        intent.putExtra("replyCount", replies.size)
                        for ((i, reply) in replies.withIndex()) {
                            intent.putExtra("replyStyle$i", reply["style"])
                            intent.putExtra("replyContent$i", reply["content"])
                        }
                        startService(intent)
                        result.success(true)
                    }
                    "hideAnalysisPanel" -> {
                        val intent = Intent(this, FloatWindowService::class.java)
                        intent.action = "HIDE_PANEL"
                        startService(intent)
                        result.success(true)
                    }
                    "updateOpacity" -> result.success(true)
                    else -> result.notImplemented()
                }
            }
    }

    private fun startMediaProjection() {
        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(projectionManager.createScreenCaptureIntent(), SCREEN_CAPTURE_REQUEST)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == SCREEN_CAPTURE_REQUEST) {
            if (resultCode == RESULT_OK && data != null) {
                val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                mediaProjection = projectionManager.getMediaProjection(resultCode, data)
                captureScreenWithMediaProjection()
            } else {
                pendingScreenshotResult?.success(null)
                pendingScreenshotResult = null
            }
        }
    }

    private fun captureScreenWithMediaProjection() {
        val metrics = DisplayMetrics()
        windowManager!!.defaultDisplay.getRealMetrics(metrics)

        imageReader = ImageReader.newInstance(metrics.widthPixels, metrics.heightPixels, PixelFormat.RGBA_8888, 2)
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScreenCapture",
            metrics.widthPixels, metrics.heightPixels, metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader!!.surface, null, null
        )

        Handler(Looper.getMainLooper()).postDelayed({
            val image = imageReader?.acquireLatestImage()
            if (image != null) {
                val bitmap = imageToBitmap(image, metrics.widthPixels, metrics.heightPixels)
                image.close()

                val finalBitmap = if (pendingCaptureArea != null) {
                    cropBitmap(bitmap, pendingCaptureArea!!)
                } else {
                    bitmap
                }

                val stream = ByteArrayOutputStream()
                finalBitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)

                if (pendingScreenshotResult != null) {
                    pendingScreenshotResult?.success(stream.toByteArray())
                } else {
                    screenshotEngine?.let { engine ->
                        MethodChannel(engine.dartExecutor.binaryMessenger, SCREENSHOT_CHANNEL)
                            .invokeMethod("onSelectionScreenshot", stream.toByteArray())
                    }
                }
            } else {
                pendingScreenshotResult?.success(null)
            }

            cleanupProjection()
            pendingScreenshotResult = null
            pendingCaptureArea = null
        }, 500)
    }

    private fun imageToBitmap(image: Image, width: Int, height: Int): Bitmap {
        val planes = image.planes
        val buffer: ByteBuffer = planes[0].buffer
        val pixelStride = planes[0].pixelStride
        val rowStride = planes[0].rowStride
        val rowPadding = rowStride - pixelStride * width

        val bitmap = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888)
        bitmap.copyPixelsFromBuffer(buffer)
        return Bitmap.createBitmap(bitmap, 0, 0, width, height)
    }

    private fun cropBitmap(bitmap: Bitmap, rect: android.graphics.Rect): Bitmap {
        val left = rect.left.coerceIn(0, bitmap.width)
        val top = rect.top.coerceIn(0, bitmap.height)
        val right = rect.right.coerceIn(left, bitmap.width)
        val bottom = rect.bottom.coerceIn(top, bitmap.height)
        val width = right - left
        val height = bottom - top
        return if (width > 0 && height > 0) {
            Bitmap.createBitmap(bitmap, left, top, width, height)
        } else {
            bitmap
        }
    }

    private fun cleanupProjection() {
        virtualDisplay?.release()
        imageReader?.close()
        mediaProjection?.stop()
        virtualDisplay = null
        imageReader = null
        mediaProjection = null
    }

    private fun showSelectionOverlay() {
        if (selectionOverlay != null) return
        if (!Settings.canDrawOverlays(this)) return

        val metrics = DisplayMetrics()
        windowManager!!.defaultDisplay.getRealMetrics(metrics)

        val rectWidth = (metrics.widthPixels * 0.7).toInt()
        val rectHeight = (metrics.heightPixels * 0.4).toInt()
        val rectLeft = (metrics.widthPixels - rectWidth) / 2
        val rectTop = (metrics.heightPixels - rectHeight) / 3

        selectionRect.set(rectLeft, rectTop, rectLeft + rectWidth, rectTop + rectHeight)

        val overlay = FrameLayout(this).apply { setBackgroundColor(0xCC000000.toInt()) }

        val selectionView = SelectionView(this).apply {
            layoutParams = FrameLayout.LayoutParams(rectWidth, rectHeight).apply {
                leftMargin = rectLeft
                topMargin = rectTop
            }
            onRectChanged = { left, top, right, bottom ->
                selectionRect.set(left, top, right, bottom)
            }
        }
        overlay.addView(selectionView)

        val buttonContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                bottomMargin = 120
            }
        }

        buttonContainer.addView(Button(this).apply {
            text = "取消"
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xFF666666.toInt())
            setOnClickListener { hideSelectionOverlay() }
        })

        buttonContainer.addView(Button(this).apply {
            text = "确认截图"
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xFF667EEA.toInt())
            setOnClickListener {
                hideSelectionOverlay()
                Handler(Looper.getMainLooper()).postDelayed({
                    val result = pendingScreenshotResult
                    pendingScreenshotResult = null
                    pendingCaptureArea = android.graphics.Rect(selectionRect)
                    startMediaProjection()
                    pendingScreenshotResult = result
                }, 300)
            }
        })

        overlay.addView(buttonContainer)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        windowManager!!.addView(overlay, params)
        selectionOverlay = overlay
    }

    private fun hideSelectionOverlay() {
        selectionOverlay?.let {
            windowManager!!.removeView(it)
            selectionOverlay = null
        }
    }

    override fun onDestroy() {
        hideSelectionOverlay()
        cleanupProjection()
        super.onDestroy()
    }
}
