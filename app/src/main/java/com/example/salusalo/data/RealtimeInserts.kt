package com.example.salusalo.data

import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.decodeRecord
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Streams rows inserted into a public table where [column] equals [value], or
 * every inserted row when no column is given. Realtime applies the table's
 * SELECT policies, so only rows the caller may read arrive. The channel is
 * removed when the collector stops.
 *
 * After a lost connection supabase-kt reconnects and joins the channel again
 * by itself, but rows inserted in between never arrive; [onRejoined] runs each
 * time the channel has joined again, so the caller can reload what it missed.
 */
internal inline fun <reified T : Any> realtimeInserts(
    table: String,
    column: String? = null,
    value: String? = null,
    noinline onRejoined: (suspend () -> Unit)? = null
): Flow<T> = flow {
    val client = SupabaseClientProvider.client
    val channel = client.channel("$table-${value ?: "all"}-${UUID.randomUUID()}")
    val changes = channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
        this.table = table
        if (column != null && value != null) filter(column, FilterOperator.EQ, value)
    }
    channel.subscribe()
    try {
        coroutineScope {
            if (onRejoined != null) launch { channel.status.rejoins().collect { onRejoined() } }
            emitAll(changes.map { it.decodeRecord<T>() })
        }
    } finally {
        withContext(NonCancellable) { client.realtime.removeChannel(channel) }
    }
}

/** Every time a channel has joined again: the first join is the initial subscription. */
internal fun Flow<RealtimeChannel.Status>.rejoins(): Flow<RealtimeChannel.Status> =
    filter { it == RealtimeChannel.Status.SUBSCRIBED }.drop(1)
