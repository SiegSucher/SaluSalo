package com.example.salusalo.data

import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.decodeRecord
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Streams rows inserted into a public table where [column] equals [value].
 * Realtime applies the table's SELECT policies, so only rows the caller may
 * read arrive. The channel is removed when the collector stops.
 */
internal inline fun <reified T : Any> realtimeInserts(table: String, column: String, value: String): Flow<T> = flow {
    val client = SupabaseClientProvider.client
    val channel = client.channel("$table-$value-${UUID.randomUUID()}")
    val changes = channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
        this.table = table
        filter(column, FilterOperator.EQ, value)
    }
    channel.subscribe()
    try {
        emitAll(changes.map { it.decodeRecord<T>() })
    } finally {
        withContext(NonCancellable) { client.realtime.removeChannel(channel) }
    }
}
