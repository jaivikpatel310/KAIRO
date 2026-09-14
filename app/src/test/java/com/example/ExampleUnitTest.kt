package com.example

import com.example.voice.IntentRouter
import com.example.voice.IntentType
import com.example.voice.ResponseTemplates
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {

  @Test
  fun testIntentRouter_whatCanYouDo() {
    val result = IntentRouter.route("What can you do?")
    assertEquals(IntentType.WHAT_CAN_YOU_DO, result.intent)
    assertFalse(result.shouldPauseListening)
    assertTrue(result.spokenResponse.isNotEmpty())
  }

  @Test
  fun testIntentRouter_areYouThere() {
    val result = IntentRouter.route("are you there")
    assertEquals(IntentType.ARE_YOU_THERE, result.intent)
    assertFalse(result.shouldPauseListening)
    assertTrue(result.spokenResponse.isNotEmpty())
  }

  @Test
  fun testIntentRouter_stopListening() {
    val result = IntentRouter.route("Please stop listening")
    assertEquals(IntentType.STOP_LISTENING, result.intent)
    assertTrue(result.shouldPauseListening)
    assertTrue(result.spokenResponse.isNotEmpty())
  }

  @Test
  fun testIntentRouter_unknownCommandFallback() {
    val result = IntentRouter.route("order a pizza from downtown")
    assertEquals(IntentType.UNKNOWN_COMMAND, result.intent)
    assertFalse(result.shouldPauseListening)
    assertTrue(result.spokenResponse.isNotEmpty())
  }

  @Test
  fun testIntentRouter_emptyInput() {
    val result = IntentRouter.route("   ")
    assertEquals(IntentType.DID_NOT_HEAR, result.intent)
    assertTrue(result.spokenResponse.isNotEmpty())
  }

  @Test
  fun testResponseTemplates_returnsValidVariants() {
    for (type in IntentType.values()) {
      val response = ResponseTemplates.getResponse(type)
      assertNotNull(response)
      assertTrue(response.isNotBlank())
    }
  }
}
