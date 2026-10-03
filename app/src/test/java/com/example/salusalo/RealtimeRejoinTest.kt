package com.example.salusalo

import com.example.salusalo.data.rejoins
import io.github.jan.supabase.realtime.RealtimeChannel.Status.SUBSCRIBED
import io.github.jan.supabase.realtime.RealtimeChannel.Status.SUBSCRIBING
import io.github.jan.supabase.realtime.RealtimeChannel.Status.UNSUBSCRIBED
import kotlinx.coroutines.flow.count
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Alerts and messages inserted while Realtime was disconnected never arrive
 * live, so each rejoin (not the first join) must trigger a reload.
 */
class RealtimeRejoinTest {
    @Test
    fun theFirstJoinIsNotARejoin() = runBlocking {
        assertEquals(0, flowOf(SUBSCRIBING, SUBSCRIBED).rejoins().count())
        // The watcher may start after the first join already happened.
        assertEquals(0, flowOf(SUBSCRIBED).rejoins().count())
    }

    @Test
    fun everyLaterJoinIsARejoin() = runBlocking {
        // A reconnect (subscribe again), then a rejoin after a channel error.
        val statuses = flowOf(SUBSCRIBING, SUBSCRIBED, SUBSCRIBING, SUBSCRIBED, UNSUBSCRIBED, SUBSCRIBING, SUBSCRIBED)
        assertEquals(2, statuses.rejoins().count())
    }
}
