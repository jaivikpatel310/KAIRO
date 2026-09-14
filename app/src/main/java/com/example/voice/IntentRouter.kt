package com.example.voice

import java.util.regex.Pattern

data class RouteResult(
    val intent: IntentType,
    val spokenResponse: String,
    val shouldPauseListening: Boolean = false
)

object IntentRouter {

    private val WHAT_CAN_YOU_DO_PATTERN = Pattern.compile(
        "\\b(what\\s+can\\s+you\\s+do|what\\s+do\\s+you\\s+do|help|capabilities|who\\s+are\\s+you|features|what\\s+are\\s+your\\s+skills)\\b",
        Pattern.CASE_INSENSITIVE
    )

    private val ARE_YOU_THERE_PATTERN = Pattern.compile(
        "\\b(are\\s+you\\s+there|you\\s+there|are\\s+you\\s+awake|are\\s+you\\s+listening|can\\s+you\\s+hear\\s+me|status|ping|hello\\s+kairo|hey\\s+kairo)\\b",
        Pattern.CASE_INSENSITIVE
    )

    private val STOP_LISTENING_PATTERN = Pattern.compile(
        "\\b(stop\\s+listening|go\\s+to\\s+sleep|sleep|stand\\s+down|pause\\s+listening|take\\s+a\\s+break|pause)\\b",
        Pattern.CASE_INSENSITIVE
    )

    fun route(rawText: String?): RouteResult {
        val text = rawText?.trim()?.lowercase() ?: ""
        if (text.isEmpty()) {
            return RouteResult(
                intent = IntentType.DID_NOT_HEAR,
                spokenResponse = ResponseTemplates.getResponse(IntentType.DID_NOT_HEAR)
            )
        }

        // Tier 1: Deterministic matching
        return when {
            WHAT_CAN_YOU_DO_PATTERN.matcher(text).find() -> {
                RouteResult(
                    intent = IntentType.WHAT_CAN_YOU_DO,
                    spokenResponse = ResponseTemplates.getResponse(IntentType.WHAT_CAN_YOU_DO)
                )
            }
            ARE_YOU_THERE_PATTERN.matcher(text).find() -> {
                RouteResult(
                    intent = IntentType.ARE_YOU_THERE,
                    spokenResponse = ResponseTemplates.getResponse(IntentType.ARE_YOU_THERE)
                )
            }
            STOP_LISTENING_PATTERN.matcher(text).find() -> {
                RouteResult(
                    intent = IntentType.STOP_LISTENING,
                    spokenResponse = ResponseTemplates.getResponse(IntentType.STOP_LISTENING),
                    shouldPauseListening = true
                )
            }
            // Tier 2: Fallback for unmatched inputs
            else -> {
                RouteResult(
                    intent = IntentType.UNKNOWN_COMMAND,
                    spokenResponse = ResponseTemplates.getResponse(IntentType.UNKNOWN_COMMAND)
                )
            }
        }
    }
}
