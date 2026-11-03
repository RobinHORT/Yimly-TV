package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.ui.YimlyExitDialog
import com.example.ui.YimlyMediaShutdown
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
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
    fun `exit media shutdown javascript targets session termination and media elements`() {
        val script = YimlyMediaShutdown.END_SESSION_JAVASCRIPT
        assertTrue("Script must target active karaoke session endpoint", script.contains("/api/karaoke/sessions/"))
        assertTrue("Script must call end action", script.contains("/end"))
        assertTrue("Script must target audio and video elements", script.contains("video, audio"))
        assertTrue("Script must invoke pause on media", script.contains(".pause()"))
        assertTrue("Script must check for website confirm button", script.contains("end-session-confirm"))
        assertTrue("Script must check for website session info button", script.contains("session-info-end-session"))
    }

    @Test
    fun `pause media javascript pauses media without ending sessions`() {
        val script = YimlyMediaShutdown.PAUSE_MEDIA_JAVASCRIPT
        assertTrue("Script must target audio and video elements", script.contains("video, audio"))
        assertTrue("Script must invoke pause on media", script.contains(".pause()"))
        assertTrue("Pause script must not terminate sessions", !script.contains("/end"))
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
}
