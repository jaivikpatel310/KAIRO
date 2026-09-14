package com.example.voice

import kotlin.random.Random

/**
 * Phrasing variant bank for Kairo.
 * Adheres strictly to Kairo's voice:
 * - Male-voiced, confident, calm, direct, action-oriented, loyal, technically sharp.
 * - Light, occasional dry humor; never robotic; no corporate hedging or sycophancy.
 * - Short confirmations; never breaks character.
 */
enum class IntentType {
    WHAT_CAN_YOU_DO,
    ARE_YOU_THERE,
    STOP_LISTENING,
    WAKE_CONFIRMATION,
    DID_NOT_HEAR,
    UNKNOWN_COMMAND
}

object ResponseTemplates {

    private val templates: Map<IntentType, List<String>> = mapOf(
        IntentType.WAKE_CONFIRMATION to listOf(
            "Listening.",
            "I'm here.",
            "Go ahead.",
            "Talk to me."
        ),
        IntentType.ARE_YOU_THERE to listOf(
            "Right here. Standing by.",
            "Always here. What do you need?",
            "Present and locked in.",
            "Right beside you. Say the word."
        ),
        IntentType.WHAT_CAN_YOU_DO to listOf(
            "Right now, I keep watch and wake on your cue. Core voice engine is locked in. More capabilities are on the way.",
            "Voice spine is active. I run continuous on-device listening and stand by for your commands. We're just getting started.",
            "I'm your voice-first companion in the phone. Right now, testing reflexes. Ask if I'm here or tell me to take a break.",
            "Core listening and speech systems are online. Basic instincts are locked in; full toolkit is on the way."
        ),
        IntentType.STOP_LISTENING to listOf(
            "Standing down. Say 'Wake up Kairo' when you need me.",
            "Powering down the mic. Wake me up whenever you're ready.",
            "Taking five. Catch you in a bit.",
            "Standing by in quiet mode."
        ),
        IntentType.DID_NOT_HEAR to listOf(
            "Didn't catch that — say it again?",
            "Missed that one. What was that?",
            "Didn't hear anything. Try again?",
            "Static on my end. Say that once more?"
        ),
        IntentType.UNKNOWN_COMMAND to listOf(
            "Don't have that one in the arsenal yet. Still learning.",
            "Not wired for that just yet. Working on it.",
            "Haven't learned that move yet. Give it time.",
            "Out of my current reach, but noted."
        )
    )

    fun getResponse(intentType: IntentType): String {
        val options = templates[intentType] ?: return "Standing by."
        val index = Random.nextInt(options.size)
        return options[index]
    }
}
