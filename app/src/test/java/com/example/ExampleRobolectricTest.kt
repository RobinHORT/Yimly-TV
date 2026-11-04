package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.ui.SessionEndResult
import com.example.ui.YimlyExitDialog
import com.example.ui.YimlyMediaShutdown
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Yimly TV", appName)
    }

    @Test
    fun `exit confirmation string resources match requirements`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("Leave Yimly-TV?", context.getString(R.string.exit_dialog_title))
        assertEquals(
            "Are you sure you want to leave? Your current karaoke session will end.",
            context.getString(R.string.exit_dialog_message)
        )
        assertEquals("Cancel", context.getString(R.string.exit_dialog_cancel))
        assertEquals("Leave & Exit", context.getString(R.string.exit_dialog_confirm))
    }

    @Test
    fun `single session-ending request issued without DOM button clicking`() {
        val script = YimlyMediaShutdown.START_SESSION_END_JAVASCRIPT

        // Must target active session endpoint
        assertTrue("Script must target active karaoke session endpoint", script.contains("/api/karaoke/sessions/"))
        assertTrue("Script must call end action", script.contains("/end"))
        assertTrue("Script must preserve same-origin credentials", script.contains("credentials: 'same-origin'"))

        // Must NOT click DOM buttons (prevents duplicate requests)
        assertFalse("Script must not click DOM buttons", script.contains(".click()"))
        assertFalse("Script must not target end-session-confirm selector", script.contains("end-session-confirm"))

        // Verify exactly ONE fetch call exists in the script
        val fetchCount = Regex("\\bfetch\\(").findAll(script).count()
        assertEquals("Script must issue exactly ONE fetch call", 1, fetchCount)

        // Must record status into window property for Android retrieval
        assertTrue("Script must set window.__yimly_exit_status", script.contains("window.__yimly_exit_status"))
    }

    @Test
    fun `successful server response reported correctly`() {
        val rawJson = """{"inRoom":true,"success":true,"status":200,"error":null}"""
        val result = YimlyMediaShutdown.parseSessionEndResult(rawJson)

        assertNotNull("Result should not be null", result)
        assertTrue("inRoom should be true", result!!.inRoom)
        assertTrue("success should be true", result.success)
        assertEquals(200, result.status)
        assertNull("error should be null", result.error)

        // Also test double-escaped JSON string from evaluateJavascript
        val doubleEscaped = "\"{\\\"inRoom\\\":true,\\\"success\\\":true,\\\"status\\\":200,\\\"error\\\":null}\""
        val escapedResult = YimlyMediaShutdown.parseSessionEndResult(doubleEscaped)
        assertNotNull("Escaped result should not be null", escapedResult)
        assertTrue("Escaped result success should be true", escapedResult!!.success)
    }

    @Test
    fun `failed server response reported correctly`() {
        val rawJson401 = """{"inRoom":true,"success":false,"status":401,"error":"HTTP error 401"}"""
        val result401 = YimlyMediaShutdown.parseSessionEndResult(rawJson401)

        assertNotNull("Result should not be null", result401)
        assertTrue("inRoom should be true", result401!!.inRoom)
        assertFalse("success should be false", result401.success)
        assertEquals(401, result401.status)
        assertEquals("HTTP error 401", result401.error)

        val rawJson500 = """{"inRoom":true,"success":false,"status":500,"error":"HTTP error 500"}"""
        val result500 = YimlyMediaShutdown.parseSessionEndResult(rawJson500)
        assertNotNull("Result should not be null", result500)
        assertFalse("success should be false on 500", result500!!.success)
        assertEquals(500, result500.status)
    }

    @Test
    fun `network failure is handled`() {
        val rawJson = """{"inRoom":true,"success":false,"status":0,"error":"Failed to fetch"}"""
        val result = YimlyMediaShutdown.parseSessionEndResult(rawJson)

        assertNotNull("Result should not be null", result)
        assertTrue("inRoom should be true", result!!.inRoom)
        assertFalse("success should be false", result.success)
        assertEquals(0, result.status)
        assertEquals("Failed to fetch", result.error)
    }

    @Test
    fun `no-room state reports clean result`() {
        val rawJson = """{"inRoom":false,"success":true,"status":200,"error":null}"""
        val result = YimlyMediaShutdown.parseSessionEndResult(rawJson)

        assertNotNull("Result should not be null", result)
        assertFalse("inRoom should be false", result!!.inRoom)
        assertTrue("success should be true", result.success)
        assertEquals(200, result.status)
    }

    @Test
    fun `null and uncompleted results return null`() {
        assertNull(YimlyMediaShutdown.parseSessionEndResult(null))
        assertNull(YimlyMediaShutdown.parseSessionEndResult(""))
        assertNull(YimlyMediaShutdown.parseSessionEndResult("null"))
        assertNull(YimlyMediaShutdown.parseSessionEndResult("\"null\""))
    }

    @Test
    fun `null webview returns immediate clean result`() = runTest {
        val result = YimlyMediaShutdown.awaitSessionEndCompletion(null, timeoutMs = 100L)
        assertFalse("inRoom should be false for null webview", result.inRoom)
        assertTrue("success should be true for null webview", result.success)
        assertEquals(200, result.status)
    }

    @Test
    fun `pause media javascript pauses media without ending sessions`() {
        val script = YimlyMediaShutdown.PAUSE_MEDIA_JAVASCRIPT
        assertTrue("Script must target audio and video elements", script.contains("video, audio"))
        assertTrue("Script must invoke pause on media", script.contains(".pause()"))
        assertFalse("Pause script must not terminate sessions", script.contains("/end"))
    }

    @Test
    fun `exit dialog renders title, message and interactive buttons`() {
        var cancelClicked = false
        var confirmClicked = false

        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = true) {
                YimlyExitDialog(
                    isExiting = false,
                    onDismiss = { cancelClicked = true },
                    onConfirmExit = { confirmClicked = true }
                )
            }
        }

        composeTestRule.onNodeWithTag("exit_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("exit_dialog_title").assertIsDisplayed().assertTextEquals("Leave Yimly-TV?")
        composeTestRule.onNodeWithTag("exit_dialog_message").assertIsDisplayed()

        composeTestRule.onNodeWithTag("exit_dialog_cancel").assertIsDisplayed()
        composeTestRule.onNodeWithTag("exit_dialog_confirm").assertIsDisplayed()

        // Test clicking Cancel
        composeTestRule.onNodeWithTag("exit_dialog_cancel").performClick()
        assertTrue("Cancel button must invoke onDismiss callback", cancelClicked)

        // Test clicking Confirm
        composeTestRule.onNodeWithTag("exit_dialog_confirm").performClick()
        assertTrue("Confirm button must invoke onConfirmExit callback", confirmClicked)
    }

    @Test
    fun `duplicate exit confirmation cannot trigger multiple requests`() {
        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = true) {
                YimlyExitDialog(
                    isExiting = true,
                    onDismiss = { },
                    onConfirmExit = { }
                )
            }
        }

        // When isExiting = true, buttons must be disabled and confirm shows 'Leaving…'
        composeTestRule.onNodeWithTag("exit_dialog_cancel").assertIsNotEnabled()
        composeTestRule.onNodeWithTag("exit_dialog_confirm").assertIsNotEnabled().assertTextEquals("Leaving…")
    }
}
