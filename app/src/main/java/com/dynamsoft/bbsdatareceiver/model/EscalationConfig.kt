package com.dynamsoft.bbsdatareceiver.model

data class EscalationConfig(
    val thresholds: List<Int> = listOf(10, 15),
    val consecutiveFrames: Int = 2,
    val minMsBetweenPrompts: Long = 5000
)
