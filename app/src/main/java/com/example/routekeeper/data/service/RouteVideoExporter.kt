package com.example.routekeeper.data.service

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.example.routekeeper.data.model.Coordinate
import com.example.routekeeper.data.model.DeliveryStop
import com.example.routekeeper.data.model.GpsBreadcrumb
import com.example.routekeeper.data.model.VideoExportSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.coroutineContext
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

data class ExportedVideoResult(
    val file: File,
    val filename: String,
    val durationSeconds: Int,
    val sampleCount: Int,
    val maxSpeedMph: Float,
    val speedingCount: Int
)

object RouteVideoExporter {

    private const val MIME_TYPE = MediaFormat.MIMETYPE_VIDEO_AVC
    private const val FPS = 20
    private const val I_FRAME_INTERVAL = 1
    private const val BIT_RATE = 2_400_000

    suspend fun exportRouteVideo(
        context: Context,
        routeName: String,
        stops: List<DeliveryStop>,
        plannedPath: List<Coordinate>,
        actualTrack: List<GpsBreadcrumb>,
        speedLimitMph: Float,
        useKmh: Boolean,
        settings: VideoExportSettings,
        onProgress: (Float, String) -> Unit
    ): ExportedVideoResult = withContext(Dispatchers.IO) {
        val useActual = settings.videoSource == "actual" && actualTrack.size >= 2

        val (width, height) = when (settings.aspectRatio) {
            "16:9" -> 1280 to 720
            "1:1" -> 720 to 720
            else -> 720 to 1280
        }

        val exportsDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val safeName = routeName.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(28)
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val suffix = if (useActual) "incident_actual_gps" else "planned_route"
        val filename = "${safeName}_${suffix}_$stamp.mp4"
        val outFile = File(exportsDir, filename)

        onProgress(0.05f, "Configuring MP4 video encoder (${width}x$height)...")

        val totalFrames = (settings.durationSeconds * FPS).coerceIn(100, 1200)

        // Build coordinate bounds covering stops, planned path, and actual GPS track
        val allPoints = mutableListOf<Coordinate>()
        stops.forEach { if (it.latitude != null && it.longitude != null) allPoints.add(Coordinate(it.latitude, it.longitude)) }
        allPoints.addAll(plannedPath)
        actualTrack.forEach { allPoints.add(Coordinate(it.latitude, it.longitude)) }

        if (allPoints.isEmpty()) {
            allPoints.add(Coordinate(51.5074, -0.1278))
        }

        val minLat = allPoints.minOf { it.latitude }
        val maxLat = allPoints.maxOf { it.latitude }
        val minLon = allPoints.minOf { it.longitude }
        val maxLon = allPoints.maxOf { it.longitude }
        val latPad = max((maxLat - minLat) * 0.16, 0.004)
        val lonPad = max((maxLon - minLon) * 0.16, 0.004)

        val bMinLat = minLat - latPad
        val bMaxLat = maxLat + latPad
        val bMinLon = minLon - lonPad
        val bMaxLon = maxLon + lonPad

        // Prepare track points to animate along
        val trackBreadcrumbs: List<GpsBreadcrumb> = if (useActual) {
            actualTrack
        } else {
            val baseCoords = if (plannedPath.size >= 2) plannedPath else stops.mapNotNull {
                if (it.latitude != null && it.longitude != null) Coordinate(it.latitude, it.longitude) else null
            }
            baseCoords.mapIndexed { idx, c ->
                GpsBreadcrumb(
                    id = "sim-$idx",
                    timestampMs = idx * 5000L,
                    timeLabel = "Stop ${idx + 1}",
                    latitude = c.latitude,
                    longitude = c.longitude,
                    speedMps = 8.9f,
                    speedMph = 20f,
                    speedKmh = 32.2f,
                    speedLimitMph = speedLimitMph,
                    isSpeeding = false
                )
            }
        }

        val maxSpeedRecorded = if (actualTrack.isNotEmpty()) actualTrack.maxOf { it.speedMph } else 0f
        val speedingCount = actualTrack.count { it.isSpeeding || it.speedMph > it.speedLimitMph }

        var codec: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var inputSurface: android.view.Surface? = null
        var muxerStarted = false
        var trackIndex = -1

        try {
            val format = MediaFormat.createVideoFormat(MIME_TYPE, width, height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
                setInteger(MediaFormat.KEY_FRAME_RATE, FPS)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL)
            }

            codec = MediaCodec.createEncoderByType(MIME_TYPE)
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            inputSurface = codec.createInputSurface()
            codec.start()

            muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val bufferInfo = MediaCodec.BufferInfo()

            val painter = VideoFramePainter(
                width = width,
                height = height,
                routeName = routeName,
                stops = stops,
                plannedPath = plannedPath,
                track = trackBreadcrumbs,
                useActual = useActual,
                speedLimitMph = speedLimitMph,
                useKmh = useKmh,
                settings = settings,
                bMinLat = bMinLat,
                bMaxLat = bMaxLat,
                bMinLon = bMinLon,
                bMaxLon = bMaxLon
            )

            for (frameIdx in 0 until totalFrames) {
                coroutineContext.ensureActive()

                val progress = frameIdx.toFloat() / max(1, totalFrames - 1)
                val canvas = inputSurface.lockCanvas(null)
                try {
                    painter.drawFrame(canvas, progress)
                } finally {
                    inputSurface.unlockCanvasAndPost(canvas)
                }

                // Drain encoded frames
                drainEncoder(codec, muxer, bufferInfo, endOfStream = false, trackIndexRef = { trackIndex }, onTrackAdded = { idx ->
                    trackIndex = idx
                    muxer.start()
                    muxerStarted = true
                }, isMuxerStarted = { muxerStarted })

                if (frameIdx % 10 == 0 || frameIdx == totalFrames - 1) {
                    val pct = 0.08f + (progress * 0.87f)
                    val stage = when {
                        useActual && progress < 0.3f -> "Rendering actual GPS route & OSM road limits (${(progress * 100).toInt()}%)..."
                        useActual -> "Embedding GPS speed telemetry & speeding markers (${(progress * 100).toInt()}%)..."
                        else -> "Rendering planned route animation (${(progress * 100).toInt()}%)..."
                    }
                    onProgress(pct, stage)
                }
            }

            codec.signalEndOfInputStream()
            onProgress(0.97f, "Finalizing MP4 video container...")
            drainEncoder(codec, muxer, bufferInfo, endOfStream = true, trackIndexRef = { trackIndex }, onTrackAdded = { idx ->
                trackIndex = idx
                muxer.start()
                muxerStarted = true
            }, isMuxerStarted = { muxerStarted })
        } catch (t: Throwable) {
            outFile.delete()
            throw t
        } finally {
            try {
                codec?.stop()
            } catch (_: Exception) {
            }
            try {
                codec?.release()
            } catch (_: Exception) {
            }
            try {
                inputSurface?.release()
            } catch (_: Exception) {
            }
            try {
                if (muxerStarted) muxer?.stop()
            } catch (_: Exception) {
            }
            try {
                muxer?.release()
            } catch (_: Exception) {
            }
        }

        onProgress(1f, "Video ready for incident review & sharing.")
        ExportedVideoResult(
            file = outFile,
            filename = filename,
            durationSeconds = settings.durationSeconds,
            sampleCount = actualTrack.size,
            maxSpeedMph = maxSpeedRecorded,
            speedingCount = speedingCount
        )
    }

    private fun drainEncoder(
        codec: MediaCodec,
        muxer: MediaMuxer,
        bufferInfo: MediaCodec.BufferInfo,
        endOfStream: Boolean,
        trackIndexRef: () -> Int,
        onTrackAdded: (Int) -> Unit,
        isMuxerStarted: () -> Boolean
    ) {
        val timeoutUs = if (endOfStream) 10_000L else 0L
        var emptyTries = 0
        while (true) {
            val status = codec.dequeueOutputBuffer(bufferInfo, timeoutUs)
            if (status == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (!endOfStream) break
                emptyTries++
                if (emptyTries > 150) break
            } else if (status == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                emptyTries = 0
                if (!isMuxerStarted()) {
                    val newIdx = muxer.addTrack(codec.outputFormat)
                    onTrackAdded(newIdx)
                }
            } else if (status >= 0) {
                emptyTries = 0
                val encodedData = codec.getOutputBuffer(status)
                if (encodedData != null) {
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                        bufferInfo.size = 0
                    }
                    if (bufferInfo.size > 0 && isMuxerStarted()) {
                        encodedData.position(bufferInfo.offset)
                        encodedData.limit(bufferInfo.offset + bufferInfo.size)
                        muxer.writeSampleData(trackIndexRef(), encodedData, bufferInfo)
                    }
                }
                codec.releaseOutputBuffer(status, false)
                if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    break
                }
            }
        }
    }

    fun saveVideoToDeviceGallery(context: Context, file: File): Uri? {
        if (!file.exists()) return null
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, file.name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/RouteKeeper")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        return try {
            val outStream = resolver.openOutputStream(uri)
                ?: throw IllegalStateException("Unable to open gallery video output stream")
            outStream.use { out ->
                FileInputStream(file).use { input ->
                    input.copyTo(out)
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Video.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            uri
        } catch (_: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            null
        }
    }

    fun shareVideoFile(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "RouteKeeper Incident & Speed Telemetry Video")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share Route Video"))
    }
}

private class VideoFramePainter(
    private val width: Int,
    private val height: Int,
    private val routeName: String,
    private val stops: List<DeliveryStop>,
    private val plannedPath: List<Coordinate>,
    private val track: List<GpsBreadcrumb>,
    private val useActual: Boolean,
    private val speedLimitMph: Float,
    private val useKmh: Boolean,
    private val settings: VideoExportSettings,
    private val bMinLat: Double,
    private val bMaxLat: Double,
    private val bMinLon: Double,
    private val bMaxLon: Double
) {
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#EEF2EA") }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#DCE4D4")
        strokeWidth = 2f
    }
    private val plannedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#93C5FD")
        strokeWidth = 6f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        pathEffect = DashPathEffect(floatArrayOf(14f, 10f), 0f)
    }
    private val trackSafePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#10B981")
        strokeWidth = 10f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val trackNearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F59E0B")
        strokeWidth = 10f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val trackSpeedingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#EF4444")
        strokeWidth = 12f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val hudCardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(232, 26, 46, 39)
    }
    private val alertCardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(240, 220, 38, 38)
    }
    private val whiteTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val mintTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#D5EA83")
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val pinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1E453B")
    }
    private val pinRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
    }
    private val vanPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1E453B")
    }
    private val headlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(120, 250, 204, 21)
    }
    private val labelBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(225, 255, 255, 255)
    }
    private val labelTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1A2E27")
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val slotBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4F46E5")
    }
    private val limitLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(190, 255, 255, 255)
        strokeWidth = 2.5f
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(8f, 6f), 0f)
    }
    private val graphLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#D5EA83")
        strokeWidth = 4f
        style = Paint.Style.STROKE
    }
    private val barTrackPaint = Paint().apply {
        color = Color.argb(90, 255, 255, 255)
        strokeWidth = 6f
        strokeCap = Paint.Cap.ROUND
    }
    private val barFillPaint = Paint().apply {
        color = Color.parseColor("#D5EA83")
        strokeWidth = 6f
        strokeCap = Paint.Cap.ROUND
    }

    private val hasBottomOverlay = settings.showSpeedTelemetry || settings.showSpeedometer
    private val hasFooterBar = settings.showScrubBar || settings.showWatermark

    private val hudBottom = if (settings.showCockpitHud) 120f else 20f
    private val panelBottom = height - (if (hasFooterBar) 48f else 20f)
    private val panelHeight = when {
        settings.showSpeedometer && settings.showSpeedTelemetry -> if (height <= 740) 204f else 238f
        settings.showSpeedTelemetry -> if (height <= 740) 160f else 188f
        settings.showSpeedometer -> 108f
        else -> 0f
    }
    private val panelTop = if (hasBottomOverlay) panelBottom - panelHeight else panelBottom

    private val mapTop = hudBottom + 16f
    private val mapBottom = max(mapTop + 140f, if (hasBottomOverlay) panelTop - 16f else panelBottom - 8f)
    private val mapLeft = width * 0.05f
    private val mapRight = width * 0.95f

    private val peakTrackLimit = if (track.isNotEmpty()) max(track.maxOf { it.speedLimitMph }, speedLimitMph) else speedLimitMph
    private val maxGraphSpeed = if (track.isNotEmpty()) max(peakTrackLimit * 1.35f, track.maxOf { it.speedMph } + 5f) else peakTrackLimit * 1.35f

    fun drawFrame(canvas: Canvas, progress: Float) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // Subtle street grid
        val step = 72f
        var gx = 0f
        while (gx < width) {
            canvas.drawLine(gx, 0f, gx, height.toFloat(), gridPaint)
            gx += step
        }
        var gy = 0f
        while (gy < height) {
            canvas.drawLine(0f, gy, width.toFloat(), gy, gridPaint)
            gy += step
        }

        // Interpolate current index along track
        val totalPts = track.size
        val floatIdx = if (totalPts > 1) progress * (totalPts - 1) else 0f
        val curIdx = floatIdx.toInt().coerceIn(0, max(0, totalPts - 1))
        val nextIdx = min(curIdx + 1, max(0, totalPts - 1))
        val frac = floatIdx - curIdx

        val curCrumb = track.getOrNull(curIdx)
        val nextCrumb = track.getOrNull(nextIdx)

        val curLat = if (curCrumb != null && nextCrumb != null) {
            curCrumb.latitude + (nextCrumb.latitude - curCrumb.latitude) * frac
        } else curCrumb?.latitude ?: 51.5074

        val curLon = if (curCrumb != null && nextCrumb != null) {
            curCrumb.longitude + (nextCrumb.longitude - curCrumb.longitude) * frac
        } else curCrumb?.longitude ?: -0.1278

        val curSpeedMph = if (curCrumb != null && nextCrumb != null) {
            curCrumb.speedMph + (nextCrumb.speedMph - curCrumb.speedMph) * frac
        } else curCrumb?.speedMph ?: 0f

        val curSpeedKmh = curSpeedMph * 1.60934f

        // Camera projection (overview vs dynamic follow)
        val isDynamic = settings.cameraMode == "dynamic-follow" && totalPts >= 2
        val latSpanFull = max(bMaxLat - bMinLat, 0.005)
        val lonSpanFull = max(bMaxLon - bMinLon, 0.005)

        val viewLatMin: Double
        val viewLatMax: Double
        val viewLonMin: Double
        val viewLonMax: Double
        if (isDynamic) {
            val zLat = latSpanFull * 0.45
            val zLon = lonSpanFull * 0.45
            viewLatMin = curLat - zLat / 2.0
            viewLatMax = curLat + zLat / 2.0
            viewLonMin = curLon - zLon / 2.0
            viewLonMax = curLon + zLon / 2.0
        } else {
            viewLatMin = bMinLat
            viewLatMax = bMaxLat
            viewLonMin = bMinLon
            viewLonMax = bMaxLon
        }

        fun toScreen(lat: Double, lon: Double): Pair<Float, Float> {
            val nx = ((lon - viewLonMin) / max(viewLonMax - viewLonMin, 0.0001)).toFloat()
            val ny = ((viewLatMax - lat) / max(viewLatMax - viewLatMin, 0.0001)).toFloat()
            val sx = mapLeft + nx * (mapRight - mapLeft)
            val sy = mapTop + ny * (mapBottom - mapTop)
            return sx to sy
        }

        // Clip map entities to viewport so dynamic-follow never paints over HUD/telemetry cards
        canvas.save()
        canvas.clipRect(mapLeft - 20f, mapTop - 12f, mapRight + 20f, mapBottom + 12f)

        // 1. Draw planned reference route dashed
        val refCoords = if (plannedPath.size >= 2) plannedPath else stops.mapNotNull {
            if (it.latitude != null && it.longitude != null) Coordinate(it.latitude, it.longitude) else null
        }
        if (refCoords.size >= 2) {
            val pPath = Path()
            refCoords.forEachIndexed { idx, c ->
                val (sx, sy) = toScreen(c.latitude, c.longitude)
                if (idx == 0) pPath.moveTo(sx, sy) else pPath.lineTo(sx, sy)
            }
            canvas.drawPath(pPath, plannedPaint)
        }

        // 2. Draw actual driven GPS trail (or simulated trail) color-coded by per-road OSM speed limit
        if (settings.showTrail && totalPts >= 2) {
            for (i in 0 until curIdx) {
                val a = track[i]
                val b = track[i + 1]
                val (x1, y1) = toScreen(a.latitude, a.longitude)
                val (x2, y2) = toScreen(b.latitude, b.longitude)
                val segSpeed = max(a.speedMph, b.speedMph)
                val segLimit = max(a.speedLimitMph, b.speedLimitMph)
                val segPaint = when {
                    a.isSpeeding || b.isSpeeding || segSpeed > segLimit -> trackSpeedingPaint
                    segSpeed >= segLimit * 0.85f -> trackNearPaint
                    else -> trackSafePaint
                }
                canvas.drawLine(x1, y1, x2, y2, segPaint)
            }
            if (curIdx < totalPts - 1) {
                val a = track[curIdx]
                val (x1, y1) = toScreen(a.latitude, a.longitude)
                val (x2, y2) = toScreen(curLat, curLon)
                val curSegLimit = a.speedLimitMph
                val segPaint = if (curSpeedMph > curSegLimit) trackSpeedingPaint else trackSafePaint
                canvas.drawLine(x1, y1, x2, y2, segPaint)
            }
        }

        // 3. Draw Stop Pins, Names & Booked Time Slots
        if (settings.showStopPins) {
            stops.forEachIndexed { idx, stop ->
                if (stop.latitude != null && stop.longitude != null) {
                    val (sx, sy) = toScreen(stop.latitude, stop.longitude)
                    pinPaint.color = if (stop.delivered) Color.parseColor("#10B981") else Color.parseColor("#1E453B")
                    canvas.drawCircle(sx, sy, 18f, pinPaint)
                    canvas.drawCircle(sx, sy, 13f, pinRingPaint)
                    canvas.drawCircle(sx, sy, 10f, pinPaint)

                    whiteTextPaint.textSize = 14f
                    whiteTextPaint.textAlign = Paint.Align.CENTER
                    canvas.drawText(stop.dropNumber ?: "${idx + 1}", sx, sy + 5f, whiteTextPaint)

                    if (settings.showStopNames) {
                        val label = stop.name.take(18)
                        labelTextPaint.textSize = 16f
                        val textW = labelTextPaint.measureText(label)
                        val boxRect = RectF(sx - textW / 2f - 10f, sy - 48f, sx + textW / 2f + 10f, sy - 22f)
                        canvas.drawRoundRect(boxRect, 8f, 8f, labelBgPaint)
                        canvas.drawText(label, sx - textW / 2f, sy - 29f, labelTextPaint)
                    }

                    if (settings.showTimeWindows && stop.timeWindow != null) {
                        val slot = stop.timeWindow.label ?: "${stop.timeWindow.startTime}-${stop.timeWindow.endTime}"
                        val slotShort = slot.take(15)
                        whiteTextPaint.textSize = 13f
                        whiteTextPaint.textAlign = Paint.Align.LEFT
                        val slotW = whiteTextPaint.measureText(slotShort)
                        val slotRect = RectF(sx - slotW / 2f - 8f, sy + 22f, sx + slotW / 2f + 8f, sy + 44f)
                        canvas.drawRoundRect(slotRect, 6f, 6f, slotBgPaint)
                        canvas.drawText(slotShort, sx - slotW / 2f, sy + 38f, whiteTextPaint)
                    }
                }
            }
        }

        // 4. Draw Moving Delivery Van + Headlights
        if (settings.showVan && curCrumb != null) {
            val (vx, vy) = toScreen(curLat, curLon)
            val headingRad = if (nextCrumb != null && curIdx != nextIdx) {
                val (nx, ny) = toScreen(nextCrumb.latitude, nextCrumb.longitude)
                atan2((ny - vy).toDouble(), (nx - vx).toDouble())
            } else 0.0

            canvas.save()
            canvas.translate(vx, vy)
            canvas.rotate(Math.toDegrees(headingRad).toFloat())

            // Headlight cone
            val cone = Path().apply {
                moveTo(14f, -6f)
                lineTo(54f, -22f)
                lineTo(54f, 22f)
                lineTo(14f, 6f)
                close()
            }
            canvas.drawPath(cone, headlightPaint)

            // Van body
            val curRoadLimitMph = curCrumb.speedLimitMph
            vanPaint.color = if (curSpeedMph > curRoadLimitMph) Color.parseColor("#DC2626") else Color.parseColor("#1E453B")
            canvas.drawRoundRect(RectF(-18f, -12f, 18f, 12f), 6f, 6f, vanPaint)
            canvas.drawRoundRect(RectF(6f, -9f, 15f, 9f), 3f, 3f, pinRingPaint)
            canvas.restore()
        }

        canvas.restore()

        // 5. Top Cockpit / Incident Header HUD
        if (settings.showCockpitHud) {
            val hudRect = RectF(24f, 20f, width - 24f, hudBottom)
            canvas.drawRoundRect(hudRect, 20f, 20f, hudCardPaint)

            mintTextPaint.textSize = 18f
            mintTextPaint.textAlign = Paint.Align.LEFT
            val headerTag = if (useActual) "ACTUAL GPS TRACK · INCIDENT REVIEW" else "PLANNED ROUTE SIMULATION"
            canvas.drawText(headerTag, 46f, 52f, mintTextPaint)

            whiteTextPaint.textSize = 26f
            whiteTextPaint.textAlign = Paint.Align.LEFT
            canvas.drawText(routeName.take(22), 46f, 88f, whiteTextPaint)

            val timeText = curCrumb?.timeLabel ?: "--:--:--"
            whiteTextPaint.textSize = 22f
            whiteTextPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText(timeText, width - 46f, 84f, whiteTextPaint)
        }

        // 6. Speeding Alert Banner + Speedometer + Embedded Speed Timeline Graph
        if (hasBottomOverlay) {
            val curRoadLimitMph = curCrumb?.speedLimitMph ?: speedLimitMph
            val curRoadLabel = curCrumb?.roadName?.take(22)?.let { " · $it" } ?: ""
            val isOverLimit = curSpeedMph > curRoadLimitMph
            val panelRect = RectF(24f, panelTop, width - 24f, panelBottom)
            canvas.drawRoundRect(panelRect, 22f, 22f, if (isOverLimit) alertCardPaint else hudCardPaint)

            val speedDisplay = if (useKmh) "${curSpeedKmh.roundToInt()} km/h" else "${curSpeedMph.roundToInt()} mph"
            val limitDisplay = if (useKmh) "${(curRoadLimitMph * 1.60934f).roundToInt()} km/h" else "${curRoadLimitMph.roundToInt()} mph"

            val statusStr = if (isOverLimit) {
                val over = if (useKmh) {
                    ((curSpeedMph - curRoadLimitMph) * 1.60934f).roundToInt().coerceAtLeast(1)
                } else {
                    (curSpeedMph - curRoadLimitMph).roundToInt().coerceAtLeast(1)
                }
                val unit = if (useKmh) "KM/H" else "MPH"
                "⚠ SPEEDING (+$over $unit · OSM $limitDisplay$curRoadLabel)"
            } else {
                "OSM LIMIT: $limitDisplay$curRoadLabel · WITHIN LIMIT"
            }

            if (settings.showSpeedometer) {
                whiteTextPaint.textSize = 40f
                whiteTextPaint.textAlign = Paint.Align.LEFT
                canvas.drawText(speedDisplay, 46f, panelTop + 48f, whiteTextPaint)

                mintTextPaint.textSize = 17f
                mintTextPaint.textAlign = Paint.Align.LEFT
                canvas.drawText(statusStr, 46f, panelTop + 76f, mintTextPaint)

                whiteTextPaint.textSize = 15f
                whiteTextPaint.textAlign = Paint.Align.RIGHT
                canvas.drawText("${"%.5f".format(curLat)}, ${"%.5f".format(curLon)}", width - 46f, panelTop + 42f, whiteTextPaint)
            } else if (settings.showSpeedTelemetry) {
                mintTextPaint.textSize = 17f
                mintTextPaint.textAlign = Paint.Align.LEFT
                canvas.drawText(statusStr, 46f, panelTop + 34f, mintTextPaint)

                whiteTextPaint.textSize = 15f
                whiteTextPaint.textAlign = Paint.Align.RIGHT
                canvas.drawText("${"%.5f".format(curLat)}, ${"%.5f".format(curLon)}", width - 46f, panelTop + 34f, whiteTextPaint)
            }

            // Mini embedded speed timeline graph inside bottom panel
            if (settings.showSpeedTelemetry && totalPts >= 2) {
                val gLeft = 46f
                val gRight = width - 46f
                val gTop = if (settings.showSpeedometer) panelTop + 92f else panelTop + 50f
                val gBottom = panelBottom - 16f
                val gHeight = max(32f, gBottom - gTop)

                // Per-road stepped OSM speed limit dashed curve
                val lPath = Path()
                track.forEachIndexed { idx, crumb ->
                    val x = gLeft + (idx.toFloat() / max(1, totalPts - 1)) * (gRight - gLeft)
                    val y = gBottom - (crumb.speedLimitMph / maxGraphSpeed) * gHeight
                    if (idx == 0) lPath.moveTo(x, y) else lPath.lineTo(x, y)
                }
                canvas.drawPath(lPath, limitLinePaint)

                // Speed curve up to curIdx
                val sPath = Path()
                for (i in 0..curIdx) {
                    val x = gLeft + (i.toFloat() / max(1, totalPts - 1)) * (gRight - gLeft)
                    val y = gBottom - (track[i].speedMph / maxGraphSpeed) * gHeight
                    if (i == 0) sPath.moveTo(x, y) else sPath.lineTo(x, y)
                }
                canvas.drawPath(sPath, graphLinePaint)
            }
        }

        // 7. Bottom Scrub Progress Bar
        if (settings.showScrubBar) {
            val barY = height - 24f
            val barRight = if (settings.showWatermark) width - 250f else width - 46f
            canvas.drawLine(46f, barY, barRight, barY, barTrackPaint)
            canvas.drawLine(46f, barY, 46f + (barRight - 46f) * progress, barY, barFillPaint)
        }

        // 8. Watermark
        if (settings.showWatermark) {
            whiteTextPaint.textSize = 14f
            whiteTextPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText("RouteKeeper · Telemetry Log", width - 46f, height - 20f, whiteTextPaint)
        }
    }
}
