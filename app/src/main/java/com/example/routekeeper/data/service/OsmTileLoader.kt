package com.example.routekeeper.data.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import com.example.routekeeper.data.model.Coordinate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sinh
import kotlin.math.tan

object OsmTileLoader {

    private const val TILE_SIZE = 256.0
    private val memoryCache = LruCache<String, Bitmap>(96)

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build()

    fun getMemoryTile(z: Int, x: Int, y: Int): Bitmap? = memoryCache.get(key(z, x, y))

    suspend fun loadTile(context: Context, z: Int, x: Int, y: Int): Bitmap? {
        val maxTile = 1 shl z
        if (z < 0 || z > 19 || y < 0 || y >= maxTile) return null
        val wrappedX = ((x % maxTile) + maxTile) % maxTile
        val cacheKey = key(z, wrappedX, y)

        memoryCache.get(cacheKey)?.let { return it }

        return withContext(Dispatchers.IO) {
            memoryCache.get(cacheKey)?.let { return@withContext it }

            val cacheDir = File(context.cacheDir, "osm_map_cache").apply { mkdirs() }
            val diskFile = File(cacheDir, "${z}_${wrappedX}_$y.png")

            if (diskFile.exists() && diskFile.length() > 100L) {
                val bmp = BitmapFactory.decodeFile(diskFile.absolutePath)
                if (bmp != null) {
                    memoryCache.put(cacheKey, bmp)
                    return@withContext bmp
                }
            }

            try {
                val url = "https://tile.openstreetmap.org/$z/$wrappedX/$y.png"
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "RouteKeeper-Android/1.0 (Delivery Route Viewer)")
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val bytes = response.body?.bytes()
                        if (bytes != null && bytes.isNotEmpty()) {
                            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            if (bmp != null) {
                                memoryCache.put(cacheKey, bmp)
                                runCatching {
                                    FileOutputStream(diskFile).use { it.write(bytes) }
                                }
                                return@withContext bmp
                            }
                        }
                    }
                }
            } catch (_: Exception) {
            }
            null
        }
    }

    private fun key(z: Int, x: Int, y: Int): String = "$z/$x/$y"

    fun lonToWorldX(lon: Double, zoom: Double): Double {
        val scale = TILE_SIZE * 2.0.pow(zoom)
        return ((lon + 180.0) / 360.0) * scale
    }

    fun latToWorldY(lat: Double, zoom: Double): Double {
        val clampedLat = lat.coerceIn(-85.0511, 85.0511)
        val latRad = Math.toRadians(clampedLat)
        val scale = TILE_SIZE * 2.0.pow(zoom)
        return (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * scale
    }

    fun worldXToLon(worldX: Double, zoom: Double): Double {
        val scale = TILE_SIZE * 2.0.pow(zoom)
        return (worldX / scale) * 360.0 - 180.0
    }

    fun worldYToLat(worldY: Double, zoom: Double): Double {
        val scale = TILE_SIZE * 2.0.pow(zoom)
        val n = PI - 2.0 * PI * (worldY / scale)
        return Math.toDegrees(atan(sinh(n)))
    }

    fun computeFitCamera(
        coords: List<Coordinate>,
        viewportWidthPx: Float,
        viewportHeightPx: Float
    ): Pair<Coordinate, Float> {
        if (coords.isEmpty()) {
            return Coordinate(51.5074, -0.1278) to 12.5f
        }
        val minLat = coords.minOf { it.latitude }
        val maxLat = coords.maxOf { it.latitude }
        val minLon = coords.minOf { it.longitude }
        val maxLon = coords.maxOf { it.longitude }
        val center = Coordinate((minLat + maxLat) / 2.0, (minLon + maxLon) / 2.0)

        if (coords.size == 1 || (maxLat - minLat < 0.0005 && maxLon - minLon < 0.0005)) {
            return center to 15.5f
        }

        val w = max(viewportWidthPx.toDouble(), 300.0) * 0.78
        val h = max(viewportHeightPx.toDouble(), 260.0) * 0.72

        for (z in 17 downTo 9) {
            val zD = z.toDouble()
            val dx = kotlin.math.abs(lonToWorldX(maxLon, zD) - lonToWorldX(minLon, zD))
            val dy = kotlin.math.abs(latToWorldY(minLat, zD) - latToWorldY(maxLat, zD))
            if (dx <= w && dy <= h) {
                return center to z.toFloat().coerceIn(10f, 16.5f)
            }
        }
        return center to 10.5f
    }
}
