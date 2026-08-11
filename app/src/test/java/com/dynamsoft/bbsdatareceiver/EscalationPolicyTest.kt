package com.dynamsoft.bbsdatareceiver

import com.dynamsoft.bbsdatareceiver.model.EscalationConfig
import com.dynamsoft.bbsdatareceiver.scanner.EscalationPolicy
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class EscalationPolicyTest {

    private lateinit var policy: EscalationPolicy

    @Before
    fun setup() {
        policy = EscalationPolicy(
            EscalationConfig(
                thresholds = listOf(10, 15),
                consecutiveFrames = 2,
                minMsBetweenPrompts = 5000
            )
        )
    }

    @Test
    fun `does not trigger below threshold`() {
        assertFalse(policy.onFrame(5, nowMs = 1000))
        assertFalse(policy.onFrame(9, nowMs = 2000))
    }

    @Test
    fun `does not trigger on single qualifying frame (needs consecutive)`() {
        assertFalse(policy.onFrame(10, nowMs = 1000))
        // One qualifying frame is not enough
    }

    @Test
    fun `triggers after consecutive qualifying frames at threshold 10`() {
        assertFalse(policy.onFrame(10, nowMs = 1000)) // 1st consecutive
        assertTrue(policy.onFrame(12, nowMs = 2000))   // 2nd consecutive → trigger
    }

    @Test
    fun `consecutive count resets on non-qualifying frame`() {
        assertFalse(policy.onFrame(10, nowMs = 1000))
        assertFalse(policy.onFrame(5, nowMs = 2000))   // below threshold, resets
        assertFalse(policy.onFrame(10, nowMs = 3000))   // starts over
    }

    @Test
    fun `decline raises threshold from 10 to 15`() {
        // Trigger at 10
        policy.onFrame(10, nowMs = 1000)
        policy.onFrame(10, nowMs = 2000)
        policy.onDecline()

        assertEquals(15, policy.currentThreshold)
        assertFalse(policy.isSuppressed)
    }

    @Test
    fun `does not trigger at old threshold after decline`() {
        policy.onFrame(10, nowMs = 1000)
        policy.onFrame(10, nowMs = 2000)
        policy.onDecline()

        // 12 barcodes: above old threshold (10) but below new threshold (15)
        assertFalse(policy.onFrame(12, nowMs = 10000))
        assertFalse(policy.onFrame(12, nowMs = 11000))
    }

    @Test
    fun `triggers at new threshold 15 after decline`() {
        policy.onFrame(10, nowMs = 1000)
        policy.onFrame(10, nowMs = 2000)
        policy.onDecline()

        // Now need 15+ for 2 consecutive frames
        assertFalse(policy.onFrame(15, nowMs = 10000))
        assertTrue(policy.onFrame(17, nowMs = 11000))
    }

    @Test
    fun `second decline suppresses permanently`() {
        // First trigger + decline
        policy.onFrame(10, nowMs = 1000)
        policy.onFrame(10, nowMs = 2000)
        policy.onDecline()

        // Second trigger + decline
        policy.onFrame(15, nowMs = 10000)
        policy.onFrame(15, nowMs = 11000)
        policy.onDecline()

        assertTrue(policy.isSuppressed)

        // Should never trigger again, even with 30 barcodes
        assertFalse(policy.onFrame(30, nowMs = 20000))
        assertFalse(policy.onFrame(30, nowMs = 21000))
    }

    @Test
    fun `accept suppresses permanently`() {
        policy.onFrame(10, nowMs = 1000)
        policy.onFrame(10, nowMs = 2000)
        policy.onAccept()

        assertTrue(policy.isSuppressed)
        assertFalse(policy.onFrame(30, nowMs = 10000))
        assertFalse(policy.onFrame(30, nowMs = 11000))
    }

    @Test
    fun `debounce prevents rapid re-prompting`() {
        // Trigger first prompt
        assertFalse(policy.onFrame(10, nowMs = 1000))
        assertTrue(policy.onFrame(10, nowMs = 2000))

        // Try to trigger again too quickly (within 5000ms debounce)
        assertFalse(policy.onFrame(10, nowMs = 3000))
        assertFalse(policy.onFrame(10, nowMs = 4000))

        // After debounce window passes
        assertFalse(policy.onFrame(10, nowMs = 8000))  // 1st consecutive
        assertTrue(policy.onFrame(10, nowMs = 9000))   // 2nd → trigger
    }

    @Test
    fun `reset restores initial state`() {
        policy.onFrame(10, nowMs = 1000)
        policy.onFrame(10, nowMs = 2000)
        policy.onDecline()
        policy.onFrame(15, nowMs = 10000)
        policy.onFrame(15, nowMs = 11000)
        policy.onDecline()

        assertTrue(policy.isSuppressed)

        policy.reset()

        assertFalse(policy.isSuppressed)
        assertEquals(10, policy.currentThreshold)

        // Should work again
        assertFalse(policy.onFrame(10, nowMs = 20000))
        assertTrue(policy.onFrame(10, nowMs = 21000))
    }
}
