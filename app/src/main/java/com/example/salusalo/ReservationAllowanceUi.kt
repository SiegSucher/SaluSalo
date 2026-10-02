package com.example.salusalo

import android.os.SystemClock
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.lifecycle.compose.LifecycleStartEffect
import com.example.salusalo.data.ReservationRepository
import com.example.salusalo.model.ReservationAllowance
import com.example.salusalo.model.allowedPickupMinutes
import com.example.salusalo.model.parseTimestamp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Instant

data class ReservationAllowanceState(
    val value: ReservationAllowance?,
    val now: Instant?,
    val fresh: Boolean,
    val error: String?,
    val retry: () -> Unit
) {
    val maxQuantity: Int get() = if (fresh) value?.canReserve ?: 0 else 0
    val allowedMinutes: List<Int> get() = if (fresh && now != null)
        value?.allowedPickupMinutes(now).orEmpty() else emptyList()
}

@Composable
internal fun rememberReservationAllowance(foodPostId: String, enabled: Boolean): ReservationAllowanceState {
    val repository = remember { ReservationRepository() }
    val scope = rememberCoroutineScope()
    var value by remember(foodPostId) { mutableStateOf<ReservationAllowance?>(null) }
    var receivedAt by remember(foodPostId) { mutableStateOf(0L) }
    var elapsed by remember(foodPostId) { mutableStateOf(SystemClock.elapsedRealtime()) }
    var error by remember(foodPostId) { mutableStateOf<String?>(null) }
    var revision by remember(foodPostId) { mutableStateOf(0) }
    var started by remember(foodPostId) { mutableStateOf(false) }

    LifecycleStartEffect(foodPostId, enabled, revision) {
        started = enabled
        val clockJob = if (enabled) scope.launch {
            while (isActive) {
                elapsed = SystemClock.elapsedRealtime()
                delay(1_000)
            }
        } else null
        val loadJob = if (enabled) scope.launch {
            while (isActive) {
                try {
                    val result = repository.getReservationAllowance(foodPostId)
                    check(parseTimestamp(result.serverNow) != null) { "Invalid server time" }
                    receivedAt = SystemClock.elapsedRealtime()
                    elapsed = receivedAt
                    value = result
                    error = null
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    error = "Couldn't check availability. Check your connection and retry."
                }
                delay(15_000)
            }
        } else null
        onStopOrDispose {
            started = false
            clockJob?.cancel()
            loadJob?.cancel()
        }
    }
    val age = (elapsed - receivedAt).coerceAtLeast(0L)
    val fresh = started && value != null && error == null && age <= 30_000
    val now = value?.serverNow?.let { parseTimestamp(it) }?.plusMillis(age)
    return ReservationAllowanceState(value, now, fresh, error) { revision++ }
}

@Composable
internal fun ReservationAllowanceSummary(state: ReservationAllowanceState, unit: String) {
    val value = state.value
    Column {
        when {
            state.error != null -> {
                Text(state.error, style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = state.retry) { Text("Retry availability check") }
            }
            !state.fresh || value == null -> Text("Checking your allowance…", style = MaterialTheme.typography.bodySmall)
            else -> {
                Text(
                    if (value.maxPerReceiver == null) "No per-receiver limit on this listing."
                    else "Limit: ${value.maxPerReceiver} $unit per receiver · Already reserved or picked up: ${value.usedQuantity}",
                    style = MaterialTheme.typography.bodySmall
                )
                Text("Available stock: ${value.availableQuantity} $unit", style = MaterialTheme.typography.bodySmall)
                Text("You can reserve up to ${value.canReserve} $unit now.", style = MaterialTheme.typography.bodyMedium)
                if (value.canReserve == 0) {
                    Text("No quantity is currently available to you. Your allowance or this listing's availability may have changed.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
