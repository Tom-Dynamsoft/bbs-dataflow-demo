package com.dynamsoft.bbsdatareceiver.scanner

import androidx.lifecycle.LifecycleOwner
import com.dynamsoft.core.basic_structures.CompletionListener
import com.dynamsoft.cvr.CaptureVisionRouter
import com.dynamsoft.cvr.CaptureVisionRouterException
import com.dynamsoft.cvr.CapturedResultReceiver
import com.dynamsoft.cvr.EnumPresetTemplate
import com.dynamsoft.dbr.BarcodeResultItem
import com.dynamsoft.dbr.DecodedBarcodesResult
import com.dynamsoft.dce.CameraEnhancer
import com.dynamsoft.dce.CameraView
import com.dynamsoft.license.LicenseManager
import android.util.Log

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

    var onBarcodesDecoded: ((items: Array<BarcodeResultItem>) -> Unit)? = null

    init {
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
                    onBarcodesDecoded?.invoke(items)
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

    fun release() {
        stopScanning()
        router.removeAllResultReceivers()
    }
}
