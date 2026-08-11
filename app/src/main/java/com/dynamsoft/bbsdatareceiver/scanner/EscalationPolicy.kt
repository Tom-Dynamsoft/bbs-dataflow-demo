package com.dynamsoft.bbsdatareceiver.scanner

import com.dynamsoft.bbsdatareceiver.model.EscalationConfig

/**
 * Framework-agnostic escalation policy. Determines when to prompt the user
 * to switch to BBS based on per-frame barcode density.
 *
 * Pure logic — no Android dependencies, fully unit-testable.
 */
class EscalationPolicy(
    var config: EscalationConfig = EscalationConfig()
) {
    private var currentThresholdIndex = 0
    private var consecutiveQualifyingFrames = 0
    private var lastPromptTimeMs = 0L
    private var suppressed = false

    val currentThreshold: Int
        get() = config.thresholds.getOrNull(currentThresholdIndex)
            ?: config.thresholds.last()

    val isSuppressed: Boolean get() = suppressed

    /**
     * Call on each decode callback with the number of unique barcodes in that frame.
     * @return true if the escalation prompt should be shown
     */
    fun onFrame(uniqueBarcodeCount: Int, nowMs: Long = System.currentTimeMillis()): Boolean {
        if (suppressed) return false

        val threshold = currentThreshold
        if (uniqueBarcodeCount >= threshold) {
            consecutiveQualifyingFrames++
        } else {
            consecutiveQualifyingFrames = 0
            return false
        }

        if (consecutiveQualifyingFrames < config.consecutiveFrames) return false

        // Debounce: don't re-prompt too quickly
        if (lastPromptTimeMs > 0 && (nowMs - lastPromptTimeMs) < config.minMsBetweenPrompts) {
            consecutiveQualifyingFrames = 0 // require fresh consecutive frames after debounce
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
    }

    /** User accepted the prompt. Suppress further prompts. */
    fun onAccept() {
        suppressed = true
        consecutiveQualifyingFrames = 0
    }

    /** Reset all state (for "Reset demo state" button). */
    fun reset() {
        currentThresholdIndex = 0
        consecutiveQualifyingFrames = 0
        lastPromptTimeMs = 0L
        suppressed = false
    }
}
