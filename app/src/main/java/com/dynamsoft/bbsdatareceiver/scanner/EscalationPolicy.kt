package com.dynamsoft.bbsdatareceiver.scanner

import com.dynamsoft.bbsdatareceiver.model.EscalationConfig

/**
 * Determines when to prompt the user to switch to BBS based on:
 * 1. Barcode density (10+ barcodes per frame)
 * 2. Result instability (results changing between consecutive callbacks)
 *
 * Both conditions must be true for escalation to trigger.
 */
class EscalationPolicy(
    var config: EscalationConfig = EscalationConfig()
) {
    private var currentThresholdIndex = 0
    private var consecutiveQualifyingFrames = 0
    private var lastPromptTimeMs = 0L
    private var suppressed = false

    /** Track previous frame's barcode keys to detect instability. */
    private var previousFrameKeys: Set<String> = emptySet()
    private var unstableFrameCount = 0

    val currentThreshold: Int
        get() = config.thresholds.getOrNull(currentThresholdIndex)
            ?: config.thresholds.last()

    val isSuppressed: Boolean get() = suppressed

    /**
     * Call on each decode callback.
     * @param uniqueBarcodeCount number of barcodes in this frame
     * @param frameKeys set of dedup keys for this frame's barcodes
     * @return true if the escalation prompt should be shown
     */
    fun onFrame(
        uniqueBarcodeCount: Int,
        frameKeys: Set<String> = emptySet(),
        nowMs: Long = System.currentTimeMillis()
    ): Boolean {
        if (suppressed) return false

        // Check density threshold
        val threshold = currentThreshold
        if (uniqueBarcodeCount >= threshold) {
            consecutiveQualifyingFrames++
        } else {
            consecutiveQualifyingFrames = 0
            previousFrameKeys = frameKeys
            unstableFrameCount = 0
            return false
        }

        // Check instability — results must be changing between frames
        if (previousFrameKeys.isNotEmpty() && frameKeys != previousFrameKeys) {
            unstableFrameCount++
        } else if (previousFrameKeys.isNotEmpty() && frameKeys == previousFrameKeys) {
            // Results stabilized — reset instability counter
            unstableFrameCount = 0
        }
        previousFrameKeys = frameKeys

        // Need both: enough consecutive dense frames AND results are unstable
        if (consecutiveQualifyingFrames < config.consecutiveFrames) return false
        if (unstableFrameCount < config.consecutiveFrames) return false

        // Debounce: don't re-prompt too quickly
        if (lastPromptTimeMs > 0 && (nowMs - lastPromptTimeMs) < config.minMsBetweenPrompts) {
            consecutiveQualifyingFrames = 0
            return false
        }

        lastPromptTimeMs = nowMs
        consecutiveQualifyingFrames = 0
        return true
    }

    /** User declined the prompt. Advance threshold or suppress. */
    fun onDecline() {
        if (currentThresholdIndex < config.thresholds.size - 1) {
            currentThresholdIndex++
        } else {
            suppressed = true
        }
        consecutiveQualifyingFrames = 0
        unstableFrameCount = 0
    }

    /** User accepted the prompt. Suppress further prompts. */
    fun onAccept() {
        suppressed = true
        consecutiveQualifyingFrames = 0
        unstableFrameCount = 0
    }

    /** Reset all state (for "Reset demo state" button). */
    fun reset() {
        currentThresholdIndex = 0
        consecutiveQualifyingFrames = 0
        lastPromptTimeMs = 0L
        suppressed = false
        previousFrameKeys = emptySet()
        unstableFrameCount = 0
    }
}
