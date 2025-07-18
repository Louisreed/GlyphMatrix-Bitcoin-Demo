package com.nothinglondon.sdkdemo.demos.bitcoin

import android.content.Context
import android.content.SharedPreferences
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
import kotlin.math.sin
import kotlin.math.abs

class BitcoinDemoService : GlyphMatrixService("Bitcoin-Demo") {
    private val TAG = "BitcoinDemo"
    private lateinit var bgScope: CoroutineScope
    private var priceUpdateJob: Job? = null
    private var scrollJob: Job? = null
    
    private var currentPrice: Double = 0.0
    private var previousPrice: Double = 0.0
    private var isLongPressing = false
    private var returnToLogoJob: Job? = null
    private var logoFadeJob: Job? = null
    private var shimmerJob: Job? = null
    private var isInitialStartup = true
    
    private lateinit var sharedPreferences: SharedPreferences
    
    private val fullPriceFormat = NumberFormat.getCurrencyInstance(Locale.US).apply {
        minimumFractionDigits = 0
        maximumFractionDigits = 0
    }

    override fun performOnServiceConnected(
        context: Context,
        glyphMatrixManager: GlyphMatrixManager
    ) {
        bgScope = CoroutineScope(Dispatchers.IO)
        
        // Reset startup flag for new connection
        isInitialStartup = true
        
        // Initialize SharedPreferences to persist price data across reconnections
        sharedPreferences = context.getSharedPreferences("bitcoin_demo_prefs", Context.MODE_PRIVATE)
        
        // Restore previous price data
        currentPrice = sharedPreferences.getFloat("current_price", 0.0f).toDouble()
        previousPrice = sharedPreferences.getFloat("previous_price", 0.0f).toDouble()
        
        Log.d(TAG, "Service connected - restored prices: current=$currentPrice, previous=$previousPrice")
        
        // Show logo immediately on startup (no fade-in delay)
        displayStaticBitcoinIcon()
        
        // Start shimmer animation immediately after showing the logo
        bgScope.launch {
            delay(100) // Small delay to ensure logo is displayed
            isInitialStartup = false // Allow price updates
            startShimmerAnimation()
        }
        
        // Start price updates after a delay to let the initial setup complete
        bgScope.launch {
            delay(2000) // Wait 2 seconds before starting price updates
            startPriceUpdates()
        }
    }

    override fun performOnServiceDisconnected(context: Context) {
        Log.d(TAG, "Service disconnected")
        
        // Save current price data before disconnecting
        if (::sharedPreferences.isInitialized) {
            sharedPreferences.edit()
                .putFloat("current_price", currentPrice.toFloat())
                .putFloat("previous_price", previousPrice.toFloat())
                .apply()
        }
        
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
        returnToLogoJob?.let { job ->
            if (job.isActive) {
                try {
                    job.cancel()
                } catch (e: Exception) {
                    // Ignore cancellation exceptions
                }
            }
        }
        logoFadeJob?.let { job ->
            if (job.isActive) {
                try {
                    job.cancel()
                } catch (e: Exception) {
                    // Ignore cancellation exceptions
                }
            }
        }
        shimmerJob?.let { job ->
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
        Log.d(TAG, "Touch pressed - showing simple price temporarily")
        cancelReturnToLogo()
        startLogoFadeOut({
            showSimplePrice()
        })
    }

    override fun onTouchPointLongPress() {
        Log.d(TAG, "Long press detected - showing scrolling ticker immediately")
        isLongPressing = true
        cancelReturnToLogo()
        
        // Start ticker immediately without any fade out delay
        showScrollingTicker()
    }

    override fun onTouchPointReleased() {
        Log.d(TAG, "Touch released")
        val wasLongPressing = isLongPressing
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
        
        if (wasLongPressing) {
            // If we were long pressing (showing ticker), show simple price
            showSimplePrice()
            scheduleReturnToLogo()
        } else {
            // Regular press, return to logo after delay
            scheduleReturnToLogo()
        }
    }

    private fun cancelReturnToLogo() {
        returnToLogoJob?.let { job ->
            if (job.isActive) {
                try {
                    job.cancel()
                } catch (e: Exception) {
                    // Ignore cancellation exceptions
                }
            }
        }
        logoFadeJob?.let { job ->
            if (job.isActive) {
                try {
                    job.cancel()
                } catch (e: Exception) {
                    // Ignore cancellation exceptions
                }
            }
        }
        shimmerJob?.let { job ->
            if (job.isActive) {
                try {
                    job.cancel()
                } catch (e: Exception) {
                    // Ignore cancellation exceptions
                }
            }
        }
    }

    private fun scheduleReturnToLogo() {
        returnToLogoJob = bgScope.launch {
            delay(5000) // Wait 5 seconds before returning to logo
            withContext(Dispatchers.Main) {
                if (!isLongPressing) {
                    Log.d(TAG, "Returning to Bitcoin logo (home state)")
                    displayBitcoinIcon() // This already calls startLogoFadeIn()
                }
            }
        }
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
                
                // Save updated prices to SharedPreferences
                if (::sharedPreferences.isInitialized) {
                    sharedPreferences.edit()
                        .putFloat("current_price", currentPrice.toFloat())
                        .putFloat("previous_price", previousPrice.toFloat())
                        .apply()
                }
                
                Log.d(TAG, "Price updated: $currentPrice (was $previousPrice)")
                
                // Show brief price update, then return to logo (but not during initial startup)
                if (!isLongPressing && !isInitialStartup) {
                    withContext(Dispatchers.Main) {
                        startLogoFadeOut({
                            showSimplePrice()
                            scheduleReturnToLogo()
                        })
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
        displaySimplePriceText(simplePrice)
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
            val trendIndicator = getTrendIndicator()
            val changeInfo = if (previousPrice > 0) {
                val change = currentPrice - previousPrice
                val changePercent = (change / previousPrice) * 100
                val changeStr = if (change > 0) "+${NumberFormat.getCurrencyInstance(Locale.US).format(change)}" 
                               else NumberFormat.getCurrencyInstance(Locale.US).format(change)
                val percentStr = if (changePercent > 0) "+%.2f%%".format(changePercent)
                               else "%.2f%%".format(changePercent)
                " $changeStr ($percentStr)"
            } else {
                ""
            }
            
            val tickerText = "    $fullPrice $trendIndicator$changeInfo    "
            
            Log.d(TAG, "Showing enhanced scrolling ticker: '$tickerText'")
            
            // Convert text to bitmap for smooth scrolling
            val textBitmap = createTextBitmap(tickerText)
            if (textBitmap == null) {
                withContext(Dispatchers.Main) {
                    displayBitmapText("Error")
                }
                return@launch
            }
            
            // Show the first frame immediately to eliminate gap
            withContext(Dispatchers.Main) {
                glyphMatrixManager?.setMatrixFrame(generateScrollFrame(textBitmap, 0.0))
            }
            
            // Use mathematical precision scrolling like animation demo
            var scrollPosition = 0.0
            val scrollSpeed = 1.0 // Pixels per frame
            val frameDelay = 30L // 30ms = 33 FPS like animation demo
            
            while (isLongPressing) {
                try {
                    delay(frameDelay) // Delay first to avoid double-rendering first frame
                    
                    scrollPosition += scrollSpeed
                    if (scrollPosition >= textBitmap.width) {
                        scrollPosition = 0.0 // Reset to beginning
                    }
                    
                    val frame = generateScrollFrame(textBitmap, scrollPosition)
                    withContext(Dispatchers.Main) {
                        glyphMatrixManager?.setMatrixFrame(frame)
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

    private fun getTrendIndicator(): String {
        return when {
            currentPrice > previousPrice -> "▲"
            currentPrice < previousPrice -> "▼"
            else -> "●"  // Neutral indicator when prices are equal
        }
    }

    private fun displayBitmapText(text: String) {
        glyphMatrixManager?.apply {
            try {
                val textBitmap = createTextBitmap(text)
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
    
    private fun displaySimplePriceText(text: String) {
        glyphMatrixManager?.apply {
            try {
                val paint = Paint().apply {
                    color = Color.WHITE
                    textSize = 12f // Smaller font to fit within matrix
                    typeface = Typeface.MONOSPACE
                    isAntiAlias = true
                    letterSpacing = -0.15f // Even tighter spacing
                }
                
                val textWidth = paint.measureText(text).toInt()
                val textHeight = 25 // Matrix height
                
                // Ensure bitmap fits within matrix constraints
                val maxWidth = 25
                val actualWidth = minOf(textWidth + 4, maxWidth)
                val bitmap = Bitmap.createBitmap(actualWidth, textHeight, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                
                // Center the text horizontally within the constrained width
                val startX = (actualWidth - textWidth) / 2f
                canvas.drawText(text, startX, 16f, paint) // Adjusted y-position for smaller font
                
                val frame = convertBitmapToMatrix(bitmap)
                setMatrixFrame(frame)
            } catch (e: Exception) {
                Log.e(TAG, "Error displaying simple price text: $text", e)
                // Fallback to simple pattern
                val frame = IntArray(25 * 25) { 0 }
                setMatrixFrame(frame)
            }
        }
    }

    private fun displayBitcoinIcon() {
        // Start the fade-in entrance transition
        startLogoFadeIn()
    }
    
    private fun displayStaticBitcoinIcon() {
        glyphMatrixManager?.apply {
            try {
                // Get the original Bitcoin logo bitmap
                val originalBitmap = GlyphMatrixUtils.drawableToBitmap(
                    ContextCompat.getDrawable(this@BitcoinDemoService, R.drawable.bitcoin_logo)
                )
                
                // Invert the colors for better visibility on glyph matrix
                val baseBitmap = invertBitmapColors(originalBitmap)
                
                // Convert to IntArray frame like other demos
                val frame = convertBitmapToMatrix(baseBitmap)
                setMatrixFrame(frame)
            } catch (e: Exception) {
                Log.e(TAG, "Error displaying static Bitcoin icon", e)
                // Fallback to simple pattern
                val frame = IntArray(25 * 25) { 0 }
                setMatrixFrame(frame)
            }
        }
    }
    
    private fun startLogoFadeIn() {
        logoFadeJob = bgScope.launch {
            try {
                // Get the original Bitcoin logo bitmap
                val originalBitmap = GlyphMatrixUtils.drawableToBitmap(
                    ContextCompat.getDrawable(this@BitcoinDemoService, R.drawable.bitcoin_logo)
                )
                
                // Invert the colors for better visibility on glyph matrix
                val baseBitmap = invertBitmapColors(originalBitmap)
                
                Log.d(TAG, "Starting Bitcoin logo fade-in transition")
                
                val fadeSteps = 20 // Number of steps for the fade
                val frameDelay = 30L // 30ms = 33 FPS like other animations
                
                // Fade in from 0% to 100%
                for (step in 0..fadeSteps) {
                    try {
                        val brightness = step.toFloat() / fadeSteps.toFloat()
                        
                        // Apply brightness to the bitmap and convert to IntArray
                        val fadedBitmap = adjustBitmapBrightness(baseBitmap, brightness)
                        val frame = convertBitmapToMatrix(fadedBitmap)
                        
                        // Display the frame directly
                        withContext(Dispatchers.Main) {
                            glyphMatrixManager?.setMatrixFrame(frame)
                        }
                        
                        delay(frameDelay)
                        
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        // Expected when animation is cancelled - exit gracefully
                        break
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in fade-in frame", e)
                        break
                    }
                }
                
                // After fade-in is complete, start shimmer animation
                Log.d(TAG, "Bitcoin logo fade-in complete, starting shimmer animation")
                
                // Start the shimmer effect
                startShimmerAnimation()
                
            } catch (e: Exception) {
                Log.e(TAG, "Error starting Bitcoin logo fade-in", e)
                // Fallback to simple pattern
                withContext(Dispatchers.Main) {
                    glyphMatrixManager?.apply {
                        val frame = IntArray(25 * 25) { 0 }
                        setMatrixFrame(frame)
                    }
                }
            }
        }
    }
    
    private fun startLogoFadeOut(onComplete: () -> Unit, fastFade: Boolean = false) {
        logoFadeJob = bgScope.launch {
            try {
                // Get the original Bitcoin logo bitmap
                val originalBitmap = GlyphMatrixUtils.drawableToBitmap(
                    ContextCompat.getDrawable(this@BitcoinDemoService, R.drawable.bitcoin_logo)
                )
                
                // Invert the colors for better visibility on glyph matrix
                val baseBitmap = invertBitmapColors(originalBitmap)
                
                Log.d(TAG, "Starting Bitcoin logo fade-out transition")
                
                // Use faster fade for long press interactions
                val fadeSteps = if (fastFade) 8 else 15 // Much faster for long press
                val frameDelay = if (fastFade) 20L else 30L // Even faster frame rate for long press
                
                // Fade out from 100% to 0%
                for (step in fadeSteps downTo 0) {
                    try {
                        val brightness = step.toFloat() / fadeSteps.toFloat()
                        
                        // Apply brightness to the bitmap and convert to IntArray
                        val fadedBitmap = adjustBitmapBrightness(baseBitmap, brightness)
                        val frame = convertBitmapToMatrix(fadedBitmap)
                        
                        // Display the frame directly
                        withContext(Dispatchers.Main) {
                            glyphMatrixManager?.setMatrixFrame(frame)
                        }
                        
                        delay(frameDelay)
                        
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        // Expected when animation is cancelled - exit gracefully
                        break
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in fade-out frame", e)
                        break
                    }
                }
                
                // After fade-out is complete, call the completion callback immediately
                Log.d(TAG, "Bitcoin logo fade-out complete")
                withContext(Dispatchers.Main) {
                    onComplete()
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error starting Bitcoin logo fade-out", e)
                // On error, still call the completion callback
                withContext(Dispatchers.Main) {
                    onComplete()
                }
            }
        }
    }

    private fun invertBitmapColors(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val invertedBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        
        for (x in 0 until width) {
            for (y in 0 until height) {
                val pixel = bitmap.getPixel(x, y)
                val alpha = Color.alpha(pixel)
                val red = 255 - Color.red(pixel)
                val green = 255 - Color.green(pixel)
                val blue = 255 - Color.blue(pixel)
                
                val invertedPixel = Color.argb(alpha, red, green, blue)
                invertedBitmap.setPixel(x, y, invertedPixel)
            }
        }
        
        return invertedBitmap
    }
    
    private fun adjustBitmapBrightness(bitmap: Bitmap, brightness: Float): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val adjustedBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        
        // Clamp brightness between 0.0 and 1.0
        val clampedBrightness = brightness.coerceIn(0.0f, 1.0f)
        
        for (x in 0 until width) {
            for (y in 0 until height) {
                val pixel = bitmap.getPixel(x, y)
                val alpha = Color.alpha(pixel)
                val red = (Color.red(pixel) * clampedBrightness).toInt()
                val green = (Color.green(pixel) * clampedBrightness).toInt()
                val blue = (Color.blue(pixel) * clampedBrightness).toInt()
                
                val adjustedPixel = Color.argb(alpha, red, green, blue)
                adjustedBitmap.setPixel(x, y, adjustedPixel)
            }
        }
        
        return adjustedBitmap
    }
    
    private fun createShimmerFrame(baseBitmap: Bitmap, shimmerPosition: Float): IntArray {
        val matrixWidth = 25
        val matrixHeight = 25
        val frame = IntArray(matrixWidth * matrixHeight)
        
        // Create a diagonal shimmer line
        val shimmerWidth = 4f // Width of the shimmer line
        val shimmerIntensity = 1.5f // How bright the shimmer is
        
        // Scale bitmap to fit within matrix while maintaining aspect ratio
        val scaledBitmap = scaleAndCenterBitmap(baseBitmap, matrixWidth, matrixHeight)
        
        // Convert bitmap to matrix, similar to convertBitmapToMatrix but with shimmer
        val startX = (matrixWidth - scaledBitmap.width) / 2
        val startY = (matrixHeight - scaledBitmap.height) / 2
        
        for (y in 0 until matrixHeight) {
            for (x in 0 until matrixWidth) {
                val sourceX = x - startX
                val sourceY = y - startY
                
                var brightness = 0
                
                if (sourceX >= 0 && sourceX < scaledBitmap.width && sourceY >= 0 && sourceY < scaledBitmap.height) {
                    val pixel = scaledBitmap.getPixel(sourceX, sourceY)
                    val baseBrightness = (Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)) / 3
                    
                    // Calculate distance from diagonal shimmer line
                    val diagonalPos = (x + y).toFloat() - shimmerPosition
                    val shimmerDistance = abs(diagonalPos)
                    
                    // Apply shimmer effect if within shimmer width
                    val shimmerEffect = if (shimmerDistance <= shimmerWidth) {
                        val shimmerStrength = (shimmerWidth - shimmerDistance) / shimmerWidth
                        1.0f + (shimmerIntensity - 1.0f) * shimmerStrength
                    } else {
                        1.0f
                    }
                    
                    brightness = (baseBrightness * shimmerEffect).toInt().coerceAtMost(255)
                }
                
                frame[y * matrixWidth + x] = brightness
            }
        }
        
        return frame
    }
    
    private fun startShimmerAnimation() {
        shimmerJob = bgScope.launch {
            try {
                // Get the original Bitcoin logo bitmap
                val originalBitmap = GlyphMatrixUtils.drawableToBitmap(
                    ContextCompat.getDrawable(this@BitcoinDemoService, R.drawable.bitcoin_logo)
                )
                
                // Invert the colors for better visibility on glyph matrix
                val baseBitmap = invertBitmapColors(originalBitmap)
                
                Log.d(TAG, "Starting Bitcoin logo shimmer animation")
                
                val frameDelay = 50L // Slower shimmer animation (20 FPS)
                val shimmerSpeed = 2.0f // Pixels per frame
                val totalDistance = 50f // Total shimmer sweep distance
                
                while (true) {
                    var shimmerPosition = -totalDistance * 0.5f // Start off-screen
                    
                    // Shimmer sweep across the logo
                    while (shimmerPosition < totalDistance * 1.5f) {
                        try {
                            val shimmerFrame = createShimmerFrame(baseBitmap, shimmerPosition)
                            
                            // Display the frame directly like animation demo
                            withContext(Dispatchers.Main) {
                                glyphMatrixManager?.setMatrixFrame(shimmerFrame)
                            }
                            
                            delay(frameDelay)
                            shimmerPosition += shimmerSpeed
                            
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            // Expected when animation is cancelled - exit gracefully
                            return@launch
                        } catch (e: Exception) {
                            Log.e(TAG, "Error in shimmer animation frame", e)
                            return@launch
                        }
                    }
                    
                    // Wait before next shimmer cycle
                    delay(2000) // 2 second pause between shimmers
                    
                } 
                
            } catch (e: Exception) {
                Log.e(TAG, "Error starting Bitcoin logo shimmer animation", e)
            }
        }
    }

    private fun convertBitmapToMatrix(bitmap: Bitmap): IntArray {
        val matrixWidth = 25
        val matrixHeight = 25
        val frame = IntArray(matrixWidth * matrixHeight)
        
        // Scale bitmap to fit within matrix while maintaining aspect ratio
        val scaledBitmap = scaleAndCenterBitmap(bitmap, matrixWidth, matrixHeight)
        
        // Center the scaled bitmap on the matrix
        val startX = (matrixWidth - scaledBitmap.width) / 2
        val startY = (matrixHeight - scaledBitmap.height) / 2
        
        for (y in 0 until matrixHeight) {
            for (x in 0 until matrixWidth) {
                val sourceX = x - startX
                val sourceY = y - startY
                
                if (sourceX >= 0 && sourceX < scaledBitmap.width && sourceY >= 0 && sourceY < scaledBitmap.height) {
                    val pixel = scaledBitmap.getPixel(sourceX, sourceY)
                    val brightness = (Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)) / 3
                    frame[y * matrixWidth + x] = if (brightness > 128) 255 else 0
                }
            }
        }
        
        return frame
    }
    
    private fun scaleAndCenterBitmap(bitmap: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
        val originalWidth = bitmap.width
        val originalHeight = bitmap.height
        
        // Calculate scale factor to fit within bounds while maintaining aspect ratio
        val scale = minOf(
            maxWidth.toFloat() / originalWidth,
            maxHeight.toFloat() / originalHeight
        )
        
        val scaledWidth = (originalWidth * scale).toInt()
        val scaledHeight = (originalHeight * scale).toInt()
        
        return Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true)
    }

} 