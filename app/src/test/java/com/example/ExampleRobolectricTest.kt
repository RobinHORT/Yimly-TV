package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.YimlySessionServiceImpl
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    private val sessionService = YimlySessionServiceImpl()

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Yimly TV", appName)
    }

    @Test
    fun `extract session id from json with id field`() {
        val json = """{"id":"c025212a-4be3-4e92-9f4f-fd718bfc5760","createdAt":"2026-08-23T00:00:00Z"}"""
        val sessionId = sessionService.extractSessionId(json)
        assertEquals("c025212a-4be3-4e92-9f4f-fd718bfc5760", sessionId)
    }

    @Test
    fun `extract session id from json with sessionId field`() {
        val json = """{"sessionId":"c025212a-4be3-4e92-9f4f-fd718bfc5760"}"""
        val sessionId = sessionService.extractSessionId(json)
        assertEquals("c025212a-4be3-4e92-9f4f-fd718bfc5760", sessionId)
    }

    @Test
    fun `extract session id from nested json`() {
        val json = """{"status":"ok","data":{"id":"c025212a-4be3-4e92-9f4f-fd718bfc5760"}}"""
        val sessionId = sessionService.extractSessionId(json)
        assertEquals("c025212a-4be3-4e92-9f4f-fd718bfc5760", sessionId)
    }

    @Test
    fun `build host room url matches specification`() {
        val sessionId = "c025212a-4be3-4e92-9f4f-fd718bfc5760"
        val expectedUrl = "https://yimly.robinhort.link/rooms/c025212a-4be3-4e92-9f4f-fd718bfc5760?role=host"
        val actualUrl = sessionService.buildHostRoomUrl(sessionId)
        assertEquals(expectedUrl, actualUrl)
    }
}
