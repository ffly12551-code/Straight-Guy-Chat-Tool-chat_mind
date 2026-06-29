package com.chatmind.chat_mind

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.DisplayMetrics
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.embedding.engine.FlutterEngineCache
import io.flutter.plugin.common.MethodChannel
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

class FloatWindowService : Service() {
    private val TAG = "FloatWindowService"
    private val SCREENSHOT_CHANNEL = "com.chatmind/screenshot"

    private var floatBallView: View? = null
    private var resultPanelView: View? = null
    private var selectionOverlay: View? = null
    private var windowManager: WindowManager? = null
    private var isPanelShowing = false  // 防止面板意外关闭

    // 面板内的视图引用
    private var riskContent: TextView? = null
    private var riskProgress: ProgressBar? = null
    private var psychContent: TextView? = null
    private var psychProgress: ProgressBar? = null
    private var intentContent: TextView? = null
    private var intentProgress: ProgressBar? = null
    private var repliesContainer: LinearLayout? = null
    private var repliesProgress: ProgressBar? = null

    private var isAnalyzing = false
    private var panelOpacity = 0.9f
    private var panelWidth = 380
    private var panelHeight = 560
    private var panelX = 0
    private var panelY = 200

    private var currentScene = "职场"
    private var currentPsychology = ""
    private var currentIntention = ""
    private var currentRiskWarning: String? = null
    private var currentReplies = mutableListOf<Map<String, String>>()

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var isProjectionReady = false  // 标记投影是否已准备好
    private val selectionRect = android.graphics.Rect()

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        startForegroundService()
    }

    private fun startForegroundService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = "chat_mind_channel"
            val channel = NotificationChannel(
                channelId,
                "ChatMind",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "ChatMind floating window service"
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)

            val notification = NotificationCompat.Builder(this, channelId)
                .setContentTitle("ChatMind")
                .setContentText("悬浮窗运行中")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .build()

            startForeground(1, notification)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            val action = intent.action
            if (action == "UPDATE_ANALYSIS") {
                val newScene = intent.getStringExtra("scene")
                if (newScene != null && newScene.isNotEmpty()) {
                    currentScene = newScene
                }
                currentPsychology = intent.getStringExtra("psychology") ?: ""
                currentIntention = intent.getStringExtra("intention") ?: ""
                currentRiskWarning = intent.getStringExtra("riskWarning")
                val replyCount = intent.getIntExtra("replyCount", 0)
                currentReplies.clear()
                for (i in 0 until replyCount) {
                    currentReplies.add(mapOf(
                        "style" to (intent.getStringExtra("replyStyle$i") ?: ""),
                        "content" to (intent.getStringExtra("replyContent$i") ?: "")
                    ))
                }
                isAnalyzing = false
                updatePanelContent()
                showMainPanel()
            } else if (action == "HIDE_RESULT") {
                hideResultPanel()
            } else if (action == "START_SCREENSHOT") {
                hideResultPanel()
                startScreenshotFlow()
            } else if (action == "MEDIA_PROJECTION_RESULT") {
                val resultCode = intent.getIntExtra("resultCode", 0)
                val data = intent.getParcelableExtra<Intent>("data")
                if (resultCode == android.app.Activity.RESULT_OK && data != null) {
                    val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                    mediaProjection = projectionManager.getMediaProjection(resultCode, data)
                    mediaProjection?.registerCallback(object : MediaProjection.Callback() {
                        override fun onStop() {
                            isProjectionReady = false
                        }
                    }, Handler(Looper.getMainLooper()))

                    // 创建持久的 ImageReader 和 VirtualDisplay
                    setupProjection()
                    showFloatBall()
                } else {
                    Toast.makeText(this, "截图权限被拒绝", Toast.LENGTH_SHORT).show()
                    stopSelf()
                }
            }
        }
        return START_STICKY
    }

    private fun startScreenshotFlow() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "请先开启悬浮窗权限", Toast.LENGTH_SHORT).show()
            return
        }
        // 检查投影是否已准备好
        if (!isProjectionReady || mediaProjection == null) {
            Toast.makeText(this, "截图权限已失效，正在重新申请...", Toast.LENGTH_SHORT).show()
            requestMediaProjection()
            return
        }
        showSelectionOverlay()
    }

    // 创建持久的截图环境（只调用一次）
    private fun setupProjection() {
        try {
            val metrics = DisplayMetrics()
            windowManager!!.defaultDisplay.getRealMetrics(metrics)

            // 创建 ImageReader
            imageReader = ImageReader.newInstance(
                metrics.widthPixels, metrics.heightPixels, PixelFormat.RGBA_8888, 2
            )

            // 创建 VirtualDisplay
            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "ScreenCapture",
                metrics.widthPixels, metrics.heightPixels, metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader!!.surface, null, null
            )

            isProjectionReady = true
            Log.d(TAG, "截图环境创建成功")
        } catch (e: Exception) {
            Log.e(TAG, "创建截图环境失败: ${e.message}")
            isProjectionReady = false
        }
    }

    private fun showSelectionOverlay() {
        hideSelectionOverlay()

        val metrics = DisplayMetrics()
        windowManager!!.defaultDisplay.getRealMetrics(metrics)

        val rectWidth = (metrics.widthPixels * 0.6).toInt()
        val rectHeight = (metrics.heightPixels * 0.4).toInt()
        val rectLeft = (metrics.widthPixels - rectWidth) / 2
        val rectTop = (metrics.heightPixels - rectHeight) / 2

        selectionRect.set(rectLeft, rectTop, rectLeft + rectWidth, rectTop + rectHeight)

        val overlay = FrameLayout(this)

        val selectionView = SelectionView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setScreenSize(metrics.widthPixels, metrics.heightPixels)
            setInitialRect(rectLeft, rectTop, rectLeft + rectWidth, rectTop + rectHeight)
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
            background = GradientDrawable().apply {
                colors = intArrayOf(0xFF666666.toInt(), 0xFF666666.toInt())
                cornerRadius = 8f
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                rightMargin = 20
            }
            setOnClickListener { hideSelectionOverlay() }
        })

        buttonContainer.addView(Button(this).apply {
            text = "确认识别"
            setTextColor(0xFFFFFFFF.toInt())
            background = GradientDrawable().apply {
                colors = intArrayOf(0xFF667EEA.toInt(), 0xFF764BA2.toInt())
                cornerRadius = 8f
            }
            setOnClickListener {
                hideSelectionOverlay()
                captureScreen()
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
            try {
                windowManager!!.removeView(it)
            } catch (e: Exception) {}
            selectionOverlay = null
        }
    }

    private fun requestMediaProjection() {
        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val intent = projectionManager.createScreenCaptureIntent()
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    private fun captureScreen() {
        // 检查投影是否准备好
        if (!isProjectionReady || imageReader == null || virtualDisplay == null) {
            Toast.makeText(this, "截图环境未准备好，请重新打开悬浮窗", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val metrics = DisplayMetrics()
            windowManager!!.defaultDisplay.getRealMetrics(metrics)

            // 延迟获取图片，等待屏幕内容刷新
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    val image = imageReader?.acquireLatestImage()
                    if (image != null) {
                        val bitmap = imageToBitmap(image, metrics.widthPixels, metrics.heightPixels)
                        image.close()

                        val croppedBitmap = cropBitmap(bitmap, selectionRect)

                        val maxDimension = 1024
                        var scaledBitmap = croppedBitmap
                        if (croppedBitmap.width > maxDimension || croppedBitmap.height > maxDimension) {
                            val scale = maxDimension.toFloat() / kotlin.math.max(croppedBitmap.width.toFloat(), croppedBitmap.height.toFloat())
                            scaledBitmap = Bitmap.createScaledBitmap(
                                croppedBitmap,
                                (croppedBitmap.width * scale).toInt(),
                                (croppedBitmap.height * scale).toInt(),
                                true
                            )
                        }

                        val stream = ByteArrayOutputStream()
                        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 60, stream)
                        val byteArray = stream.toByteArray()

                        sendScreenshotToFlutter(byteArray)
                        isAnalyzing = true
                        showMainPanel()
                    } else {
                        Log.e(TAG, "未能获取截图图片")
                        Toast.makeText(this@FloatWindowService, "截图失败，请重试", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "处理截图时出错: ${e.message}")
                    Toast.makeText(this@FloatWindowService, "截图失败，请重试", Toast.LENGTH_SHORT).show()
                }
            }, 300)
        } catch (e: Exception) {
            Log.e(TAG, "截图时出错: ${e.message}")
            Toast.makeText(this, "截图失败，请重试", Toast.LENGTH_SHORT).show()
        }
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

    // cleanupProjection 现在什么都不做，因为截图环境是持久化的
    private fun cleanupProjection() {
        // 不释放资源，保持截图环境持久化
    }

    // 完全清理投影（包括停止），在关闭悬浮球时调用
    private fun stopProjectionCompletely() {
        try {
            virtualDisplay?.release()
        } catch (e: Exception) {}
        try {
            imageReader?.close()
        } catch (e: Exception) {}
        try {
            mediaProjection?.stop()
        } catch (e: Exception) {}
        virtualDisplay = null
        imageReader = null
        mediaProjection = null
        isProjectionReady = false
    }

    private fun sendScreenshotToFlutter(byteArray: ByteArray) {
        val engine = FlutterEngineCache.getInstance().get("chat_mind_engine")
        if (engine != null) {
            val args = hashMapOf<String, Any>(
                "image" to byteArray,
                "scene" to currentScene
            )
            MethodChannel(engine.dartExecutor.binaryMessenger, SCREENSHOT_CHANNEL)
                .invokeMethod("onSelectionScreenshot", args)
        } else {
            Toast.makeText(this, "请先打开APP", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showFloatBall() {
        if (floatBallView != null) return
        if (!Settings.canDrawOverlays(this)) {
            Log.e(TAG, "无悬浮窗权限")
            return
        }

        try {
            val ball = FrameLayout(this)
            val drawable = GradientDrawable().apply {
                colors = intArrayOf(0xFF667EEA.toInt(), 0xFF764BA2.toInt())
                shape = GradientDrawable.OVAL
            }
            ball.background = drawable

            val icon = ImageView(this).apply {
                setImageDrawable(ContextCompat.getDrawable(context, android.R.drawable.ic_dialog_info))
                colorFilter = android.graphics.PorterDuffColorFilter(0xFFFFFFFF.toInt(), android.graphics.PorterDuff.Mode.SRC_IN)
                layoutParams = FrameLayout.LayoutParams(60, 60).apply {
                    gravity = Gravity.CENTER
                }
            }
            ball.addView(icon)

            val params = WindowManager.LayoutParams(
                120, 120,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else
                    WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = 0
                y = 300
            }

            var initialX = 0
            var initialY = 0
            var touchX = 0f
            var touchY = 0f

            ball.setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        touchX = event.rawX
                        touchY = event.rawY
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (kotlin.math.abs(event.rawX - touchX) > 10 || kotlin.math.abs(event.rawY - touchY) > 10) {
                            params.x = initialX + (event.rawX - touchX).toInt()
                            params.y = initialY + (event.rawY - touchY).toInt()
                            windowManager!!.updateViewLayout(ball, params)
                        }
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (kotlin.math.abs(event.rawX - touchX) < 10 && kotlin.math.abs(event.rawY - touchY) < 10) {
                            if (resultPanelView == null) {
                                showMainPanel()
                            } else {
                                hideResultPanel()
                            }
                        } else {
                            val displayWidth = resources.displayMetrics.widthPixels
                            val targetX = if (params.x + 60 < displayWidth / 2) 0 else displayWidth - 120
                            params.x = targetX
                            windowManager!!.updateViewLayout(ball, params)
                        }
                        true
                    }
                    else -> false
                }
            }

            windowManager!!.addView(ball, params)
            floatBallView = ball
        } catch (e: Exception) {
            Log.e(TAG, "显示悬浮球失败 - ${e.message}")
        }
    }

    private fun hideFloatBall() {
        floatBallView?.let {
            try {
                windowManager!!.removeView(it)
            } catch (e: Exception) {}
            floatBallView = null
        }
    }

    private fun showMainPanel() {
        Log.d(TAG, "showMainPanel: isPanelShowing=$isPanelShowing")
        hideResultPanelInternal()  // 内部方法，不触发显示悬浮球

        if (!Settings.canDrawOverlays(this)) return

        try {
            val panel = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(20, 20, 20, 20)
                background = GradientDrawable().apply {
                    colors = intArrayOf(
                        (0xF21A1A1A.toInt() and 0xFFFFFF) or ((panelOpacity * 255).toInt() shl 24),
                        (0xF21A1A1A.toInt() and 0xFFFFFF) or ((panelOpacity * 255).toInt() shl 24)
                    )
                    cornerRadius = 20f
                }
                alpha = panelOpacity
            }

            // 第一行：场景切换
            val headerRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = 16 }
            }

            val sceneBtn = Button(this).apply {
                text = "$currentScene ▼"
                setTextColor(0xFF667EEA.toInt())
                textSize = 13f
                background = GradientDrawable().apply {
                    colors = intArrayOf(0xFF2A2A2A.toInt(), 0xFF2A2A2A.toInt())
                    cornerRadius = 8f
                }
                setPadding(16, 8, 16, 8)
                setOnClickListener { showSceneSelector() }
            }
            headerRow.addView(sceneBtn)

            headerRow.addView(View(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
            })

            val closeBtn = Button(this).apply {
                text = "×"
                setTextColor(0xFF888888.toInt())
                textSize = 18f
                background = null
                setPadding(16, 8, 0, 8)
                setOnClickListener { hideResultPanel() }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            headerRow.addView(closeBtn)

            panel.addView(headerRow)

            // 创建下区域容器（"风险提示"以下的全部内容，可滚动）
            val density = resources.displayMetrics.density
            val contentScrollView = ScrollView(this).apply {
                tag = "content_scroll"
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    0,
                    1f  // weight=1，占据剩余空间
                )
                isVerticalScrollBarEnabled = true
                isFillViewport = true
            }

            val contentContainer = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            // 风险提示
            val riskRow = createResultRow("⚠️ 风险提示",
                if (currentRiskWarning.isNullOrEmpty()) "暂无风险" else currentRiskWarning!!,
                true)
            riskContent = riskRow.second
            riskProgress = riskRow.third
            contentContainer.addView(riskRow.first)

            // 对方心理
            val psychRow = createResultRow("💭 对方心理",
                if (currentPsychology.isEmpty()) "等待分析..." else currentPsychology)
            psychContent = psychRow.second
            psychProgress = psychRow.third
            contentContainer.addView(psychRow.first)

            // 对方意图
            val intentRow = createResultRow("🎯 对方意图",
                if (currentIntention.isEmpty()) "等待分析..." else currentIntention)
            intentContent = intentRow.second
            intentProgress = intentRow.third
            contentContainer.addView(intentRow.first)

            // 回复建议标题
            val repliesTitle = TextView(this).apply {
                text = "💬 回复建议"
                setTextColor(0xFFB0B0B0.toInt())
                textSize = 12f
                setPadding(0, 8, 0, 8)
            }
            contentContainer.addView(repliesTitle)

            // 回复列表容器
            repliesContainer = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            repliesProgress = ProgressBar(this).apply {
                layoutParams = LinearLayout.LayoutParams(40, 40).apply {
                    gravity = Gravity.CENTER
                }
                visibility = if (isAnalyzing) View.VISIBLE else View.GONE
            }
            repliesContainer!!.addView(repliesProgress)

            if (currentReplies.isNotEmpty()) {
                updateReplies()
            }
            contentContainer.addView(repliesContainer)

            // 将下区域内容容器加入ScrollView
            contentScrollView.addView(contentContainer)
            panel.addView(contentScrollView)

            // 底部固定按钮行（三个按钮在同一行，紧贴面板下边缘）
            val btnHeight = (36 * density).toInt()  // 36dp高度，更小巧
            val bottomRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(8, 4, 8, 8)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            // 截屏分析按钮（苹果极简风格）
            val captureBtn = Button(this).apply {
                text = "▢"
                setTextColor(0xFFB0B0B0.toInt())
                textSize = 14f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                background = GradientDrawable().apply {
                    colors = intArrayOf(0xFF2A2A2A.toInt(), 0xFF2A2A2A.toInt())
                    cornerRadius = 10f
                }
                layoutParams = LinearLayout.LayoutParams(0, btnHeight, 1f).apply {
                    rightMargin = 4
                }
                setOnClickListener {
                    hideResultPanelInternal()
                    showSelectionOverlay()
                }
            }
            bottomRow.addView(captureBtn)

            // 设置按钮（苹果极简风格）
            val settingsBtn = Button(this).apply {
                text = "⚙"
                setTextColor(0xFFB0B0B0.toInt())
                textSize = 14f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                background = GradientDrawable().apply {
                    colors = intArrayOf(0xFF2A2A2A.toInt(), 0xFF2A2A2A.toInt())
                    cornerRadius = 10f
                }
                layoutParams = LinearLayout.LayoutParams(0, btnHeight, 1f).apply {
                    rightMargin = 4
                }
                setOnClickListener {
                    val intent = Intent(this@FloatWindowService, MainActivity::class.java)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    intent.action = "OPEN_SETTINGS"
                    startActivity(intent)
                    hideResultPanel()
                }
            }
            bottomRow.addView(settingsBtn)

            // 关闭悬浮球按钮（苹果极简风格）
            val closeServiceBtn = Button(this).apply {
                text = "✕"
                setTextColor(0xFFFF6B6B.toInt())
                textSize = 14f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                background = GradientDrawable().apply {
                    colors = intArrayOf(0xFF2A2A2A.toInt(), 0xFF2A2A2A.toInt())
                    cornerRadius = 10f
                    setStroke(1, 0x44FF6B6B.toInt())
                }
                layoutParams = LinearLayout.LayoutParams(0, btnHeight, 1f)
                setOnClickListener {
                    hideFloatBall()
                    hideResultPanelInternal()
                    stopProjectionCompletely()
                    stopSelf()
                    Toast.makeText(this@FloatWindowService, "已关闭悬浮球", Toast.LENGTH_SHORT).show()
                }
            }
            bottomRow.addView(closeServiceBtn)

            panel.addView(bottomRow)

            val params = WindowManager.LayoutParams(
                panelWidth,
                panelHeight,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else
                    WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = panelX
                y = panelY
            }

            var initialX = 0
            var initialY = 0
            var touchX = 0f
            var touchY = 0f
            var isDragging = false
            var resizeCorner = 0  // 0=none, 1=topleft, 2=topright, 3=bottomleft, 4=bottomright
            val resizeThreshold = 180  // 大幅增大角落触摸范围
            var initCornerX = 0f
            var initCornerY = 0f
            var initPanelWidth = 0
            var initPanelHeight = 0
            var lastScale = 1f

            // 存储ScrollView引用用于区域判断
            val scrollViewRef = panel.findViewWithTag("content_scroll") as? ScrollView

            panel.setOnTouchListener { view, event ->
                val rawX = event.rawX
                val rawY = event.rawY
                val viewWidth = view.width
                val viewHeight = view.height
                val scaleRatio = viewWidth.toFloat() / viewHeight.toFloat()

                // 计算触摸点相对于面板的位置
                val localX = rawX - params.x
                val localY = rawY - params.y

                // 检测是否在四个角区域（增大范围）
                val isTopLeft = localX < resizeThreshold && localY < resizeThreshold
                val isTopRight = localX > viewWidth - resizeThreshold && localY < resizeThreshold
                val isBottomLeft = localX < resizeThreshold && localY > viewHeight - resizeThreshold
                val isBottomRight = localX > viewWidth - resizeThreshold && localY > viewHeight - resizeThreshold

                // 检测是否在下区域（回复列表ScrollView区域）
                var isInScrollArea = false
                scrollViewRef?.let { scrollView ->
                    val scrollLocation = IntArray(2)
                    scrollView.getLocationOnScreen(scrollLocation)
                    val scrollTop = scrollLocation[1] - params.y
                    val scrollBottom = scrollTop + scrollView.height
                    if (localY >= scrollTop && localY <= scrollBottom && localX >= 0 && localX <= viewWidth) {
                        isInScrollArea = true
                    }
                }

                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        touchX = rawX
                        touchY = rawY

                        resizeCorner = when {
                            isTopLeft -> 1
                            isTopRight -> 2
                            isBottomLeft -> 3
                            isBottomRight -> 4
                            else -> 0
                        }

                        if (resizeCorner != 0) {
                            initCornerX = rawX
                            initCornerY = rawY
                            initPanelWidth = params.width
                            initPanelHeight = params.height
                            lastScale = 1f
                        }

                        // 只有在非角落区域才允许拖动，且不在ScrollView区域内
                        isDragging = resizeCorner == 0 && !isInScrollArea
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (isDragging) {
                            params.x = initialX + (rawX - touchX).toInt()
                            params.y = initialY + (rawY - touchY).toInt()
                            windowManager!!.updateViewLayout(panel, params)
                            panelX = params.x
                            panelY = params.y
                        } else if (resizeCorner != 0) {
                            // 平滑缩放：向面板内滑动缩小，向面板外滑动放大
                            val deltaX = rawX - initCornerX
                            val deltaY = rawY - initCornerY

                            // 计算缩放因子：向面板内为负（缩小），向面板外为正（放大）
                            val scaleDelta = when (resizeCorner) {
                                1 -> -(deltaX + deltaY) / 300f  // 左上角：向右下(面板内)缩小，向左上(面板外)放大
                                2 -> (deltaX - deltaY) / 300f   // 右上角：向左下(面板内)缩小，向右上(面板外)放大
                                3 -> (-deltaX + deltaY) / 300f  // 左下角：向右上(面板内)缩小，向左下(面板外)放大
                                4 -> (deltaX + deltaY) / 300f   // 右下角：向左上(面板内)缩小，向右下(面板外)放大
                                else -> 0f
                            }

                            val newScale = (1f + scaleDelta).coerceIn(0.6f, 2.0f)
                            if (kotlin.math.abs(newScale - lastScale) > 0.01f) {
                                val newWidth = (initPanelWidth * newScale).toInt().coerceIn(320, resources.displayMetrics.widthPixels - 40)
                                val newHeight = (initPanelHeight * newScale).toInt().coerceIn(480, resources.displayMetrics.heightPixels - 40)

                                params.width = newWidth
                                params.height = newHeight

                                // 根据角落调整位置
                                when (resizeCorner) {
                                    1 -> {
                                        params.x = initialX - (newWidth - initPanelWidth)
                                        params.y = initialY - (newHeight - initPanelHeight)
                                    }
                                    2 -> {
                                        params.y = initialY - (newHeight - initPanelHeight)
                                    }
                                    3 -> {
                                        params.x = initialX - (newWidth - initPanelWidth)
                                    }
                                }

                                windowManager!!.updateViewLayout(panel, params)
                                panelWidth = params.width
                                panelHeight = params.height
                                panelX = params.x
                                panelY = params.y

                                // 同步更新按钮大小（跟随面板宽度变化）
                                val btnSizeScale = (panelWidth.toFloat() / 380f).coerceIn(0.8f, 1.5f)
                                val newBtnHeight = (48 * density * btnSizeScale).toInt()
                                captureBtn.layoutParams = captureBtn.layoutParams.apply { height = newBtnHeight }
                                settingsBtn.layoutParams = settingsBtn.layoutParams.apply { height = newBtnHeight }
                                closeServiceBtn.layoutParams = closeServiceBtn.layoutParams.apply { height = newBtnHeight }
                                val btnFontSize = (12 * btnSizeScale).coerceIn(10f, 18f)
                                captureBtn.textSize = btnFontSize
                                settingsBtn.textSize = btnFontSize
                                closeServiceBtn.textSize = btnFontSize

                                lastScale = newScale
                            }
                        }
                    }
                    MotionEvent.ACTION_UP -> {
                        isDragging = false
                        resizeCorner = 0
                    }
                }
                // 如果在ScrollView区域内且不在角落，返回false让ScrollView处理滚动
                if (isInScrollArea && resizeCorner == 0) {
                    false
                } else {
                    true  // 拦截拖动和缩放事件
                }
            }

            windowManager!!.addView(panel, params)
            resultPanelView = panel
            isPanelShowing = true
        } catch (e: Exception) {
            Log.e(TAG, "显示主面板失败 - ${e.message}")
        }
    }

    private fun createResultRow(title: String, content: String, isRisk: Boolean = false): Triple<LinearLayout, TextView, ProgressBar> {
        val contentTextView: TextView
        val progressBar: ProgressBar

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12, 12, 12, 12)
            background = GradientDrawable().apply {
                colors = intArrayOf(
                    if (isRisk && !currentRiskWarning.isNullOrEmpty()) 0x33FF6B6B.toInt() else 0xFF222222.toInt(),
                    if (isRisk && !currentRiskWarning.isNullOrEmpty()) 0x33FF6B6B.toInt() else 0xFF222222.toInt()
                )
                cornerRadius = 12f
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 12 }
        }

        val titleView = TextView(this).apply {
            text = title
            setTextColor(if (isRisk && !currentRiskWarning.isNullOrEmpty()) 0xFFFF6B6B.toInt() else 0xFFB0B0B0.toInt())
            textSize = 12f
            setPadding(0, 0, 0, 6)
        }
        row.addView(titleView)

        val contentRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        progressBar = ProgressBar(this).apply {
            layoutParams = LinearLayout.LayoutParams(24, 24).apply {
                rightMargin = 8
            }
            visibility = if (isAnalyzing) View.VISIBLE else View.GONE
        }
        contentRow.addView(progressBar)

        contentTextView = TextView(this).apply {
            text = content
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 14f
            setLineSpacing(4f, 1f)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        contentRow.addView(contentTextView)

        row.addView(contentRow)

        return Triple(row, contentTextView, progressBar)
    }

    private fun updateReplies() {
        repliesContainer?.let { container ->
            // 移除所有子视图（除了 ProgressBar）
            while (container.childCount > 1) {
                container.removeViewAt(1)
            }
            repliesProgress?.visibility = View.GONE

            for (reply in currentReplies) {
                val replyCard = FrameLayout(this).apply {
                    setPadding(12, 12, 12, 12)
                    background = GradientDrawable().apply {
                        colors = intArrayOf(0xFF2A2A2A.toInt(), 0xFF2A2A2A.toInt())
                        cornerRadius = 10f
                    }
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { bottomMargin = 8 }
                }

                val replyLayout = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT
                    ).apply { rightMargin = 50 }
                }

                val styleView = TextView(this).apply {
                    text = "【${reply["style"]}】"
                    setTextColor(0xFF667EEA.toInt())
                    textSize = 12f
                    setPadding(0, 0, 0, 4)
                }
                replyLayout.addView(styleView)

                val contentView = TextView(this).apply {
                    text = reply["content"]
                    setTextColor(0xFFFFFFFF.toInt())
                    textSize = 13f
                    setLineSpacing(3f, 1f)
                }
                replyLayout.addView(contentView)

                val copyBtn = Button(this).apply {
                    text = "复制"
                    setTextColor(0xFF51CF66.toInt())
                    textSize = 11f
                    background = null
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        gravity = Gravity.CENTER_VERTICAL or Gravity.END
                    }
                    setOnClickListener {
                        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("reply", reply["content"]))
                        Toast.makeText(this@FloatWindowService, "已复制", Toast.LENGTH_SHORT).show()
                    }
                }

                replyCard.addView(replyLayout)
                replyCard.addView(copyBtn)
                container.addView(replyCard)
            }
        }
    }

    private fun updatePanelContent() {
        riskContent?.text = if (currentRiskWarning.isNullOrEmpty()) "暂无风险" else currentRiskWarning
        psychContent?.text = if (currentPsychology.isEmpty()) "等待分析..." else currentPsychology
        intentContent?.text = if (currentIntention.isEmpty()) "等待分析..." else currentIntention
        
        riskProgress?.visibility = View.GONE
        psychProgress?.visibility = View.GONE
        intentProgress?.visibility = View.GONE
        repliesProgress?.visibility = View.GONE
        
        if (currentReplies.isNotEmpty()) {
            updateReplies()
        }
    }

    private var sceneSelectorView: View? = null

    private fun showSceneSelector() {
        hideSceneSelector()
        if (!Settings.canDrawOverlays(this)) return

        try {
            val scenes = arrayOf("职场", "亲密关系", "家庭", "社交")
            
            val container = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(20, 20, 20, 20)
                background = GradientDrawable().apply {
                    colors = intArrayOf(0xFF2A2A2A.toInt(), 0xFF2A2A2A.toInt())
                    cornerRadius = 16f
                }
            }

            val title = TextView(this).apply {
                text = "选择场景"
                setTextColor(0xFFFFFFFF.toInt())
                textSize = 15f
                setPadding(0, 0, 0, 16)
            }
            container.addView(title)

            for ((index, scene) in scenes.withIndex()) {
                val isSelected = scene == currentScene
                val btn = Button(this).apply {
                    text = if (isSelected) "✓ $scene" else scene
                    setTextColor(if (isSelected) 0xFF667EEA.toInt() else 0xFFFFFFFF.toInt())
                    background = GradientDrawable().apply {
                        colors = intArrayOf(
                            if (isSelected) 0xFF333333.toInt() else 0xFF222222.toInt(),
                            if (isSelected) 0xFF333333.toInt() else 0xFF222222.toInt()
                        )
                        cornerRadius = 8f
                    }
                    textSize = 14f
                    setPadding(16, 12, 16, 12)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        bottomMargin = 8
                    }
                    setOnClickListener {
                        currentScene = scenes[index]
                        hideSceneSelector()
                        hideResultPanel()
                        showMainPanel()
                    }
                }
                container.addView(btn)
            }

            val cancelBtn = Button(this).apply {
                text = "取消"
                setTextColor(0xFF888888.toInt())
                background = null
                textSize = 14f
                setOnClickListener { hideSceneSelector() }
            }
            container.addView(cancelBtn)

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else
                    WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.CENTER
            }

            windowManager!!.addView(container, params)
            sceneSelectorView = container
        } catch (e: Exception) {
            Log.e(TAG, "显示场景选择器失败 - ${e.message}")
        }
    }

    private fun hideSceneSelector() {
        sceneSelectorView?.let {
            try {
                windowManager!!.removeView(it)
            } catch (e: Exception) {}
            sceneSelectorView = null
        }
    }

    private fun showResultPanel() {
        showMainPanel()
    }

    // 内部方法，不触发显示悬浮球
    private fun hideResultPanelInternal() {
        Log.d(TAG, "hideResultPanelInternal: resultPanelView=${resultPanelView != null}")
        if (resultPanelView == null) return
        resultPanelView?.let {
            try {
                windowManager!!.removeView(it)
            } catch (e: Exception) {}
            resultPanelView = null
        }
        isPanelShowing = false
        Log.d(TAG, "hideResultPanelInternal: done, isPanelShowing=$isPanelShowing")
    }

    // 公开方法，触发显示悬浮球
    private fun hideResultPanel() {
        Log.d(TAG, "hideResultPanel called")
        if (resultPanelView == null) return
        hideResultPanelInternal()
        showFloatBall()  // 面板关闭时重新显示悬浮球
    }

    override fun onDestroy() {
        isPanelShowing = false
        hideFloatBall()
        hideResultPanelInternal()
        hideSelectionOverlay()
        hideSceneSelector()
        stopProjectionCompletely()
        super.onDestroy()
    }
}
