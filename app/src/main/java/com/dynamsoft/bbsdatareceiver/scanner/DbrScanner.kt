package com.dynamsoft.bbsdatareceiver.scanner

import androidx.lifecycle.LifecycleOwner
import com.dynamsoft.core.basic_structures.CompletionListener
import com.dynamsoft.cvr.CaptureVisionRouter
import com.dynamsoft.cvr.CaptureVisionRouterException
import com.dynamsoft.cvr.CapturedResultReceiver
import com.dynamsoft.cvr.EnumPresetTemplate
import com.dynamsoft.dbr.DecodedBarcodesResult
import com.dynamsoft.core.basic_structures.ImageData
import com.dynamsoft.core.basic_structures.DSRect
import com.dynamsoft.dce.CameraEnhancer
import com.dynamsoft.dce.CameraView
import com.dynamsoft.license.LicenseManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.util.Log
import java.io.ByteArrayOutputStream

private const val TAG = "DbrScanner"

/**
 * Wraps CameraEnhancer + CaptureVisionRouter for live barcode scanning.
 * Follows the official sample lifecycle: open camera first, then start capturing.
 */
class DbrScanner(
    private val cameraView: CameraView,
    private val lifecycleOwner: LifecycleOwner
) {
    companion object {
        const val DBR_LICENSE_KEY = "t0089pwAAAGZ7UxlY/10A8yNxM5oT3kgMrRRBI8koTl43D8l/ihFUz4j4JhpON5ZEvtgHPIceFk/E/tVCQVJMpd+kHvPhu2qDb3WijziPKfPHH0+mWRc/AJbfIwU="

        private var licenseInitialized = false

        fun initLicense(key: String = DBR_LICENSE_KEY, onResult: (Boolean, String?) -> Unit = { _, _ -> }) {
            if (licenseInitialized) return
            licenseInitialized = true
            LicenseManager.initLicense(key) { isSuccess, error ->
                Log.d(TAG, "License init: success=$isSuccess, error=${error?.message}")
                onResult(isSuccess, error?.message)
            }
        }
    }

    private val camera: CameraEnhancer = CameraEnhancer(cameraView, lifecycleOwner)
    private val router: CaptureVisionRouter = CaptureVisionRouter()

    /** Scan region as percentage (0-1). Only barcodes within this region are decoded. */
    var scanRegionRect: DSRect? = null
        private set

    /** Latest video frame from the camera listener — stored as raw ImageData for on-demand conversion. */
    var latestFrame: ImageData? = null
        private set

    var onBarcodesDecoded: ((result: DecodedBarcodesResult) -> Unit)? = null

    init {
        camera.addListener { frame, _ ->
            latestFrame = frame
        }
        try {
            router.setInput(camera)
        } catch (e: CaptureVisionRouterException) {
            throw RuntimeException("Failed to set camera input", e)
        }

        router.addResultReceiver(object : CapturedResultReceiver {
            override fun onDecodedBarcodesReceived(result: DecodedBarcodesResult) {
                val items = result.items
                val uniqueTexts = items?.map { "${it.formatString}|${it.text}" }?.distinct()?.size ?: 0
                Log.d(TAG, "onDecodedBarcodesReceived: ${items?.size ?: 0} items, $uniqueTexts unique")
                if (items != null && items.isNotEmpty()) {
                    onBarcodesDecoded?.invoke(result)
                }
            }
        })
    }

    fun startScanning() {
        Log.d(TAG, "startScanning() called")
        camera.open()
        router.startCapturing(EnumPresetTemplate.PT_READ_BARCODES, object : CompletionListener {
            override fun onSuccess() {
                Log.d(TAG, "startCapturing onSuccess")
            }
            override fun onFailure(errorCode: Int, errorString: String?) {
                Log.e(TAG, "startCapturing onFailure: $errorCode $errorString")
            }
        })
    }

    fun stopScanning() {
        Log.d(TAG, "stopScanning() called")
        camera.close()
        router.stopCapturing()
    }

    /** Convert the latest stored video frame to a Bitmap on demand, applying sensor orientation. */
    fun getLatestFrameAsBitmap(): Bitmap? {
        val frame = latestFrame ?: return null
        Log.d(TAG, "getLatestFrameAsBitmap: frame ${frame.width}x${frame.height}, orientation=${frame.orientation}")
        val yuvImage = YuvImage(frame.bytes, ImageFormat.NV21, frame.width, frame.height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, frame.width, frame.height), 90, out)
        val jpegBytes = out.toByteArray()
        val raw = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size) ?: return null

        // Rotate bitmap to match the coordinate space used by the barcode decoder
        val orientation = frame.orientation
        if (orientation != 0) {
            val matrix = Matrix().apply { postRotate(orientation.toFloat()) }
            val rotated = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
            if (rotated !== raw) raw.recycle()
            Log.d(TAG, "Rotated bitmap from ${frame.width}x${frame.height} to ${rotated.width}x${rotated.height} (orientation=$orientation)")
            return rotated
        }
        return raw
    }

    /**
     * Set the scan region as percentages (0-1). Only barcodes in this area are decoded.
     * Pass null to scan the full frame.
     */
    fun setScanRegion(left: Float = 0f, top: Float = 0f, right: Float = 1f, bottom: Float = 1f) {
        val rect = DSRect(left, top, right, bottom, true)
        scanRegionRect = rect
        camera.scanRegion = rect
        Log.d(TAG, "Scan region set: left=$left, top=$top, right=$right, bottom=$bottom")
    }

    /**
     * Compute and apply scan region to match the visible area of the CameraView.
     * CameraView center-crops the frame to fill its bounds. This calculates
     * which portion of the (rotated) frame is actually visible.
     */
    fun setScanRegionToVisibleArea() {
        val viewW = cameraView.width
        val viewH = cameraView.height
        if (viewW <= 0 || viewH <= 0) {
            Log.w(TAG, "CameraView not laid out yet ($viewW x $viewH), deferring scan region")
            return
        }

        val frame = latestFrame
        if (frame == null) {
            Log.w(TAG, "No frame yet, deferring scan region")
            return
        }

        // Frame dimensions after rotation (portrait)
        val frameW: Int
        val frameH: Int
        if (frame.orientation == 90 || frame.orientation == 270) {
            frameW = frame.height
            frameH = frame.width
        } else {
            frameW = frame.width
            frameH = frame.height
        }

        val viewAspect = viewW.toFloat() / viewH
        val frameAspect = frameW.toFloat() / frameH

        val left: Float
        val top: Float
        val right: Float
        val bottom: Float

        if (frameAspect < viewAspect) {
            // Frame is taller than view (common in portrait) — crop top/bottom
            val visibleFraction = frameAspect / viewAspect
            val margin = (1f - visibleFraction) / 2f
            left = 0f
            right = 1f
            top = margin
            bottom = 1f - margin
        } else {
            // Frame is wider than view — crop left/right
            val visibleFraction = viewAspect / frameAspect
            val margin = (1f - visibleFraction) / 2f
            top = 0f
            bottom = 1f
            left = margin
            right = 1f - margin
        }

        Log.d(TAG, "Visible area: view=${viewW}x${viewH}, frame=${frameW}x${frameH} (raw ${frame.width}x${frame.height} orient=${frame.orientation}), region=[$left,$top,$right,$bottom]")
        setScanRegion(left, top, right, bottom)
    }

    /**
     * Crop a bitmap to the current scan region. Barcode coordinates are relative to
     * the full frame, so we also need to offset them when annotating the cropped bitmap.
     */
    fun cropToScanRegion(bitmap: Bitmap): Bitmap {
        val region = scanRegionRect ?: return bitmap
        val left = (region.left * bitmap.width).toInt().coerceIn(0, bitmap.width)
        val top = (region.top * bitmap.height).toInt().coerceIn(0, bitmap.height)
        val right = (region.right * bitmap.width).toInt().coerceIn(left, bitmap.width)
        val bottom = (region.bottom * bitmap.height).toInt().coerceIn(top, bitmap.height)
        val w = right - left
        val h = bottom - top
        if (w <= 0 || h <= 0) return bitmap
        return Bitmap.createBitmap(bitmap, left, top, w, h)
    }

    /** Get the scan region offset in pixels for the given bitmap size (for coordinate adjustment). */
    fun getScanRegionOffset(bitmapWidth: Int, bitmapHeight: Int): Pair<Int, Int> {
        val region = scanRegionRect ?: return 0 to 0
        val offsetX = (region.left * bitmapWidth).toInt()
        val offsetY = (region.top * bitmapHeight).toInt()
        return offsetX to offsetY
    }

    fun release() {
        stopScanning()
        router.removeAllResultReceivers()
        latestFrame = null
    }
}
