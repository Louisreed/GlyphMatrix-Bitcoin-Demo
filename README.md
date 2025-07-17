# Glyph Matrix Example Project

## About the Demo

This example project contains multiple toy demos:

- `basic` demo which shows the application icon
  - `Touch-down` (press down) to increment and display a counter
  - `Touch-up` (release) to stop incrementing the counter
- `glyphbutton` demo which shows a randomly populated grid
  - `Long-press` the `Glyph Button` on the device to generate a new random grid
- `animation` demo which shows an indefinite animation until the toy is deactivated
- `bitcoin` demo which shows a live Bitcoin price tracker with professional UI
  - Shows **actual Bitcoin logo** from Wikipedia at startup
  - `Touch` to display simplified price (e.g., "120K")
  - `Long-press` for detailed scrolling ticker with full price and trend
  - Real-time price updates every 30 seconds
  - Professional bitmap-based rendering for smooth animations

After going through the `Setup` stage in this document the demo project can be run on the device.

> Tip: `Short-press` the `Glyph Button` to navigate between the toys.

The demo project already contains the necessary libraries (GlyphMatrix SDK) and source structure as an example. However, if you want to install libraries for your own application, please reference the [**SDK documentation**](https://github.com/KenFeng04/GlyphMatrix-Development-Kit).

This demo is written in Kotlin, it also utilize a useful Kotlin wrapper `GlyphMatrixService.kt`，wrap around the original SDK that you can use in your own project.

https://github.com/user-attachments/assets/4dbaf7d1-fed0-4a1e-a0eb-38d9cbde046e

## How to Build a Professional Glyph Matrix App

### 🎯 Key Lessons Learned

This project demonstrates how to build a professional-grade glyph matrix application using the **Bitcoin Price Tracker** as a real-world example. Here are the essential insights:

#### 1. **Use Real Images, Not Hand-Drawn Graphics**

**❌ Don't Do This:**

```kotlin
// Creating manual pixel art - low quality, hard to maintain
val manualBitmap = Bitmap.createBitmap(25, 25, Bitmap.Config.ARGB_8888)
// ... manual pixel drawing
```

**✅ Do This Instead:**

```kotlin
// Use actual images with GlyphMatrixUtils - professional quality
val bitcoinObject = GlyphMatrixObject.Builder()
    .setImageSource(
        GlyphMatrixUtils.drawableToBitmap(
            ContextCompat.getDrawable(this, R.drawable.bitcoin_logo)
        )
    )
    .setScale(100)
    .setPosition(0, 0)
    .build()
```

**Why:** The SDK is designed to work with actual images. Download real logos/icons and use `GlyphMatrixUtils.drawableToBitmap()` for professional results.

#### 2. **Optimize Performance with Bitmap Rendering**

**❌ Choppy Animations:**

```kotlin
// Direct text rendering - can be choppy
glyphMatrixManager.displayText("Scrolling text...")
```

**✅ Smooth Animations:**

```kotlin
// Bitmap-based rendering with mathematical precision
private fun createTextBitmap(text: String): Bitmap {
    val paint = Paint().apply {
        color = Color.WHITE
        textSize = 16f
        typeface = Typeface.DEFAULT_BOLD
        isAntiAlias = true
    }
    val bitmap = Bitmap.createBitmap(textWidth, 25, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawText(text, 0f, 18f, paint)
    return bitmap
}

// Use 30ms delays for 33 FPS like professional animations
delay(30L)
```

#### 3. **Implement Professional Touch Interactions**

```kotlin
class BitcoinDemoService : GlyphMatrixService("Bitcoin-Demo") {

    override fun onTouchPointPressed() {
        // Immediate response - show simple price
        showSimplePrice()
    }

    override fun onTouchPointLongPress() {
        // Rich interaction - show detailed scrolling ticker
        showScrollingTicker()
    }

    override fun onTouchPointReleased() {
        // Clean state management
        isLongPressing = false
        showSimplePrice()
    }
}
```

#### 4. **Handle API Integration Professionally**

```kotlin
private suspend fun fetchBitcoinPrice() {
    try {
        val url = URL("https://api.coingecko.com/api/v3/simple/price?ids=bitcoin&vs_currencies=usd")
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 10000
        connection.readTimeout = 10000
        connection.setRequestProperty("User-Agent", "Bitcoin-Demo/1.0")

        // Proper error handling
        if (connection.responseCode == HttpURLConnection.HTTP_OK) {
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            val jsonObject = JSONObject(response)
            currentPrice = jsonObject.getJSONObject("bitcoin").getDouble("usd")
        }
    } catch (e: Exception) {
        Log.e(TAG, "Error fetching price", e)
    }
}
```

#### 5. **Manage Coroutines and Lifecycle Properly**

```kotlin
private lateinit var bgScope: CoroutineScope
private var priceUpdateJob: Job? = null

override fun performOnServiceConnected(context: Context, glyphMatrixManager: GlyphMatrixManager) {
    bgScope = CoroutineScope(Dispatchers.IO)
    startPriceUpdates()
}

override fun performOnServiceDisconnected(context: Context) {
    stopAllUpdates()
    bgScope.cancel()
}

private fun stopAllUpdates() {
    priceUpdateJob?.cancel()
    scrollJob?.cancel()
}
```

### 🚀 Step-by-Step Development Process

#### Step 1: Download Real Images

```bash
curl -o app/src/main/res/drawable/bitcoin_logo.png \
  "https://upload.wikimedia.org/wikipedia/commons/thumb/4/46/Bitcoin.svg/300px-Bitcoin.svg.png"
```

#### Step 2: Follow the Official SDK Pattern

```kotlin
// Always use the documented GlyphMatrixObject pattern
val imageObject = GlyphMatrixObject.Builder()
    .setImageSource(GlyphMatrixUtils.drawableToBitmap(drawable))
    .build()

val frame = GlyphMatrixFrame.Builder()
    .addTop(imageObject)
    .build(context)

glyphMatrixManager.setMatrixFrame(frame.render())
```

#### Step 3: Implement State Management

```kotlin
// Track app state for better UX
private var hasShownSimplePriceYet = false
private var isLongPressing = false
private var currentPrice: Double = 0.0
private var previousPrice: Double = 0.0
```

#### Step 4: Add Professional UI Logic

```kotlin
private fun formatSimplePrice(price: Double): String {
    return when {
        price >= 1000000 -> "${(price / 1000000).toInt()}M"
        price >= 1000 -> "${(price / 1000).toInt()}K"
        else -> price.toInt().toString()
    }
}
```

### 📋 Performance Best Practices

1. **Use 30ms Frame Delays:** Match the animation demo's 33 FPS timing
2. **Implement Proper Cancellation:** Always cancel coroutines in `performOnServiceDisconnected()`
3. **Cache Bitmaps:** Don't recreate bitmaps on every frame
4. **Handle Touch States:** Track interaction states for better UX
5. **Use Mathematical Precision:** Floating-point positions for smooth scrolling

### 🎨 UI/UX Guidelines

1. **Start with Logo:** Show your app's logo/icon on startup
2. **Progressive Disclosure:** Simple view → detailed view on interaction
3. **Smooth Animations:** Use bitmap rendering for professional quality
4. **Consistent Timing:** 30ms delays for smooth animations
5. **Error Handling:** Graceful fallbacks when APIs fail

### 🔧 AndroidManifest.xml Configuration

```xml
<!-- Bitcoin Demo Service -->
<service
    android:name=".demos.bitcoin.BitcoinDemoService"
    android:exported="true">
    <intent-filter>
        <action android:name="com.nothing.glyph.TOY" />
    </intent-filter>

    <meta-data
        android:name="com.nothing.glyph.toy.name"
        android:resource="@string/toy_name_bitcoin" />
    <meta-data
        android:name="com.nothing.glyph.toy.image"
        android:resource="@drawable/bitcoin_thumbnail" />
    <meta-data
        android:name="com.nothing.glyph.toy.summary"
        android:resource="@string/toy_summary_bitcoin" />
    <meta-data
        android:name="com.nothing.glyph.toy.longpress"
        android:value="1" />
</service>
```

### 📚 Additional Resources

- [Official GlyphMatrix SDK Documentation](https://github.com/Nothing-Developer-Programme/GlyphMatrix-Developer-Kit)
- [Bitcoin Demo Source Code](app/src/main/java/com/nothinglondon/sdkdemo/demos/bitcoin/BitcoinDemoService.kt)
- [GlyphMatrixService Wrapper](app/src/main/java/com/nothinglondon/sdkdemo/demos/GlyphMatrixService.kt)

---

## Requirements

Android Studio, Kotlin, compatible device with Glyph Matrix

## Setup

**1.** Prepare your Nothing device and connect it to the computer for development

**2.** Clone this project or download this repository as a ZIP and uncompress it to your local directory.

**3.** Open a new windows in Android studio and hit file on the menu bar, select open.

<p align="center">
<img src="images/open.png" alt="Open Project" style="max-height: 300px;">
</p>

**4.** Select the directory where you have cloned the repository or the unzipped folder and click `Open`

<p align="center">
<img src="images/select.png" alt="Select Project" style="max-height: 300px;">
</p>

**5.** Once the Gradle files have been synced and your phone is connected properly, you should see your device name shown at the top and a play button. Click the play button to install this example project.

<p align="center">
<img src="images/run.png" alt="Run Project" style="max-height: 300px;">
</p>

## Running a Toy

When the example project is installed on the device, toys within the project needs to be acivated before it can be used.

<table>
<tr>
<td width="60%" valign="top">

**1.** Open the `Glyph Interface` from your device settigns.

**2.** Tap on the first button on the right menu to move toys to the enabled state.

</td>
<td width="40%" align="center">
<img src="images/toy_carousoul.png" alt="Disabled Toys" style="max-height: 300px;">
</td>
</tr>

<tr>
<td width="60%" valign="top">

**3.** Use the handle bars to drag a toy from `Disabled` to `Active` state.

</td>
<td width="40%" align="center">
<img src="images/toy_disable.png" alt="Moving Toys" style="max-height: 300px;">
</td>
</tr>

<tr>
<td width="60%" valign="top">

**4.** The toys should now be in the `Active` state, and can be viewed on the Glyph Matrix using Glyph Touch.

</td>
<td width="40%" align="center">
<img src="images/toy_active.png" alt="Active Toys" style="max-height: 300px;">
</td>
</tr>
</table>
