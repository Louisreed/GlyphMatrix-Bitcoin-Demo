package com.nothinglondon.sdkdemo.demos.bitcoin

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.Log
import androidx.core.content.ContextCompat
import com.nothing.ketchum.GlyphMatrixFrame
import com.nothing.ketchum.GlyphMatrixManager
import com.nothing.ketchum.GlyphMatrixObject
import com.nothing.ketchum.GlyphMatrixUtils
import com.nothinglondon.sdkdemo.R
import com.nothinglondon.sdkdemo.demos.GlyphMatrixService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.NumberFormat
import java.util.Locale

class BitcoinDemoService : GlyphMatrixService("Bitcoin-Demo") {
    private val TAG = "BitcoinDemo"
    private lateinit var bgScope: CoroutineScope
    private var priceUpdateJob: Job? = null
    private var scrollJob: Job? = null
    
    private var currentPrice: Double = 0.0
    private var previousPrice: Double = 0.0
    private var isLongPressing = false
    private var hasShownSimplePriceYet = false
    
    private val fullPriceFormat = NumberFormat.getCurrencyInstance(Locale.US).apply {
        minimumFractionDigits = 0
        maximumFractionDigits = 0
    }

    override fun performOnServiceConnected(
        context: Context,
        glyphMatrixManager: GlyphMatrixManager
    ) {
        bgScope = CoroutineScope(Dispatchers.IO)
        Log.d(TAG, "Service connected")
        startPriceUpdates()
        displayBitcoinIcon()
    }

    override fun performOnServiceDisconnected(context: Context) {
        Log.d(TAG, "Service disconnected")
        stopAllUpdates()
        bgScope.cancel()
    }

    private fun stopAllUpdates() {
        priceUpdateJob?.let { job ->
            if (job.isActive) {
                try {
                    job.cancel()
                } catch (e: Exception) {
                    // Ignore cancellation exceptions
                }
            }
        }
        scrollJob?.let { job ->
            if (job.isActive) {
                try {
                    job.cancel()
                } catch (e: Exception) {
                    // Ignore cancellation exceptions
                }
            }
        }
        isLongPressing = false
    }

    override fun onTouchPointPressed() {
        Log.d(TAG, "Touch pressed - showing simple price")
        hasShownSimplePriceYet = true
        showSimplePrice()
    }

    override fun onTouchPointLongPress() {
        Log.d(TAG, "Long press detected - showing scrolling ticker")
        isLongPressing = true
        showScrollingTicker()
    }

    override fun onTouchPointReleased() {
        Log.d(TAG, "Touch released")
        isLongPressing = false
        scrollJob?.let { job ->
            if (job.isActive) {
                try {
                    job.cancel()
                } catch (e: Exception) {
                    // Ignore cancellation exceptions - they're expected
                }
            }
        }
        showSimplePrice()
    }

    private fun startPriceUpdates() {
        priceUpdateJob = bgScope.launch {
            while (true) {
                try {
                    fetchBitcoinPrice()
                    delay(30000) // Update every 30 seconds
                } catch (e: kotlinx.coroutines.CancellationException) {
                    // Expected when service is disconnected - exit gracefully
                    break
                } catch (e: Exception) {
                    Log.e(TAG, "Error updating price", e)
                    delay(60000) // Retry after 1 minute on error
                }
            }
        }
    }

    private suspend fun fetchBitcoinPrice() {
        try {
            val url = URL("https://api.coingecko.com/api/v3/simple/price?ids=bitcoin&vs_currencies=usd")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            connection.setRequestProperty("User-Agent", "Bitcoin-Demo/1.0")

            val responseCode = connection.responseCode
            Log.d(TAG, "API Response Code: $responseCode")
            
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                Log.d(TAG, "API Response: $response")
                
                val jsonObject = JSONObject(response)
                val bitcoinObject = jsonObject.getJSONObject("bitcoin")
                val newPrice = bitcoinObject.getDouble("usd")

                previousPrice = currentPrice
                currentPrice = newPrice
                
                Log.d(TAG, "Price updated: $currentPrice (was $previousPrice)")
                
                // Only update display if not long pressing and we've already shown simple price
                if (!isLongPressing && hasShownSimplePriceYet) {
                    withContext(Dispatchers.Main) {
                        showSimplePrice()
                    }
                }
            } else {
                Log.e(TAG, "HTTP Error: $responseCode")
                val errorResponse = connection.errorStream?.bufferedReader()?.use { it.readText() }
                Log.e(TAG, "Error response: $errorResponse")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching Bitcoin price: ${e.message}", e)
        }
    }

    private fun showSimplePrice() {
        if (currentPrice <= 0) {
            Log.d(TAG, "No price data available, showing Bitcoin icon")
            displayBitcoinIcon()
            return
        }
        
        val simplePrice = formatSimplePrice(currentPrice)
        
        Log.d(TAG, "Showing simple price: '$simplePrice'")
        displayBitmapText(simplePrice)
    }

    private fun showScrollingTicker() {
        scrollJob = bgScope.launch {
            if (currentPrice <= 0) {
                Log.d(TAG, "No price data for ticker, showing placeholder")
                withContext(Dispatchers.Main) {
                    displayBitmapText("Loading...")
                }
                return@launch
            }
            
            val fullPrice = fullPriceFormat.format(currentPrice)
            val trendText = getTrendText()
            val changeAmount = if (previousPrice > 0) {
                val change = currentPrice - previousPrice
                val changeStr = if (change > 0) "+${NumberFormat.getCurrencyInstance(Locale.US).format(change)}" 
                               else NumberFormat.getCurrencyInstance(Locale.US).format(change)
                " $changeStr"
            } else {
                ""
            }
            
            val tickerText = "    $fullPrice $trendText$changeAmount    "
            
            Log.d(TAG, "Showing scrolling ticker: '$tickerText'")
            
            // Convert text to bitmap for smooth scrolling
            val textBitmap = createTextBitmap(tickerText)
            if (textBitmap == null) {
                withContext(Dispatchers.Main) {
                    displayBitmapText("Error")
                }
                return@launch
            }
            
            // Use mathematical precision scrolling like animation demo
            var scrollPosition = 0.0
            val scrollSpeed = 1.0 // Pixels per frame
            val frameDelay = 30L // 30ms = 33 FPS like animation demo
            
            while (isLongPressing) {
                try {
                    val frame = generateScrollFrame(textBitmap, scrollPosition)
                    withContext(Dispatchers.Main) {
                        glyphMatrixManager?.setMatrixFrame(frame)
                    }
                    
                    delay(frameDelay) // Consistent 30ms timing like animation demo
                    
                    scrollPosition += scrollSpeed
                    if (scrollPosition >= textBitmap.width) {
                        scrollPosition = 0.0 // Reset to beginning
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    // Expected when touch is released - exit gracefully
                    break
                } catch (e: Exception) {
                    Log.e(TAG, "Error in ticker animation", e)
                    break
                }
            }
        }
    }

    private fun createTextBitmap(text: String, textSize: Float = 16f): Bitmap? {
        try {
            val paint = Paint().apply {
                color = Color.WHITE
                this.textSize = textSize
                typeface = Typeface.DEFAULT_BOLD
                isAntiAlias = true
            }
            
            val textWidth = paint.measureText(text).toInt()
            val textHeight = 25 // Matrix height
            
            // Create bitmap with extra padding for smooth scrolling
            val bitmap = Bitmap.createBitmap(textWidth + 50, textHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            
            // Draw text
            canvas.drawText(text, 25f, 18f, paint)
            
            return bitmap
        } catch (e: Exception) {
            Log.e(TAG, "Error creating text bitmap", e)
            return null
        }
    }

    private fun generateScrollFrame(bitmap: Bitmap, scrollPosition: Double): IntArray {
        val matrixWidth = 25
        val matrixHeight = 25
        val frame = IntArray(matrixWidth * matrixHeight)
        
        val startX = scrollPosition.toInt()
        
        for (y in 0 until matrixHeight) {
            for (x in 0 until matrixWidth) {
                val sourceX = (startX + x) % bitmap.width
                val sourceY = y
                
                if (sourceX < bitmap.width && sourceY < bitmap.height) {
                    val pixel = bitmap.getPixel(sourceX, sourceY)
                    val brightness = (Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)) / 3
                    frame[y * matrixWidth + x] = if (brightness > 128) 255 else 0
                }
            }
        }
        
        return frame
    }

    private fun formatSimplePrice(price: Double): String {
        return when {
            price >= 1000000 -> "${(price / 1000000).toInt()}M"
            price >= 1000 -> "${(price / 1000).toInt()}K"
            else -> price.toInt().toString()
        }
    }

    private fun getTrendText(): String {
        return when {
            currentPrice > previousPrice -> "UP"
            currentPrice < previousPrice -> "DOWN"
            else -> "UP"  // Default to UP when prices are equal
        }
    }

    private fun displayBitmapText(text: String) {
        glyphMatrixManager?.apply {
            try {
                val textBitmap = createTextBitmap(text, 12f) // Smaller text size for simple price
                if (textBitmap != null) {
                    val frame = convertBitmapToMatrix(textBitmap)
                    setMatrixFrame(frame)
                } else {
                    Log.e(TAG, "Failed to create bitmap for text: $text")
                    // Fallback to simple pattern
                    val frame = IntArray(25 * 25) { 0 }
                    setMatrixFrame(frame)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error displaying bitmap text: $text", e)
            }
        }
    }

    private fun displayBitcoinIcon() {
        glyphMatrixManager?.apply {
            try {
                // Use the proper GlyphMatrixObject approach with the Bitcoin logo drawable
                val bitcoinObject = GlyphMatrixObject.Builder()
                    .setImageSource(
                        GlyphMatrixUtils.drawableToBitmap(
                            ContextCompat.getDrawable(this@BitcoinDemoService, R.drawable.bitcoin_logo)
                        )
                    )
                    .setScale(100)
                    .setOrientation(0)
                    .setPosition(0, 0)
                    .build()

                val frame = GlyphMatrixFrame.Builder()
                    .addTop(bitcoinObject)
                    .build(applicationContext)

                setMatrixFrame(frame.render())
            } catch (e: Exception) {
                Log.e(TAG, "Error displaying Bitcoin icon", e)
                // Fallback to simple pattern
                val frame = IntArray(25 * 25) { 0 }
                setMatrixFrame(frame)
            }
        }
    }

    private fun convertBitmapToMatrix(bitmap: Bitmap): IntArray {
        val matrixWidth = 25
        val matrixHeight = 25
        val frame = IntArray(matrixWidth * matrixHeight)
        
        // Center the bitmap on the matrix
        val startX = (matrixWidth - bitmap.width) / 2
        val startY = (matrixHeight - bitmap.height) / 2
        
        for (y in 0 until matrixHeight) {
            for (x in 0 until matrixWidth) {
                val sourceX = x - startX
                val sourceY = y - startY
                
                if (sourceX >= 0 && sourceX < bitmap.width && sourceY >= 0 && sourceY < bitmap.height) {
                    val pixel = bitmap.getPixel(sourceX, sourceY)
                    val brightness = (Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)) / 3
                    frame[y * matrixWidth + x] = if (brightness > 128) 255 else 0
                }
            }
        }
        
        return frame
    }

} 