package com.example.salusalo

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.salusalo.data.PhoneContactRepository
import com.example.salusalo.data.normalizePickupPhoneNumber
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Key the whole scope so changing accounts cancels requests and discards phone data. */
@Composable
internal fun PhoneContactSettings(profileId: String?) {
    key(profileId) { PhoneContactSettingsContent(profileId) }
}

@Composable
private fun PhoneContactSettingsContent(profileId: String?) {
    if (profileId == null) return
    val repository = remember { PhoneContactRepository() }
    val scope = rememberCoroutineScope()
    // Deliberately not rememberSaveable: do not persist contact details in UI saved state.
    var phone by remember { mutableStateOf("") }
    var share by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var retry by remember { mutableStateOf(0) }
    var feedback by remember { mutableStateOf("") }
    LaunchedEffect(profileId, retry) {
        loading = true
        feedback = ""
        try {
            val contact = repository.getMyContact()
            phone = contact.phoneNumber.orEmpty()
            share = contact.sharePhoneForPickup
            loaded = true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            loaded = false
            feedback = "Phone settings are unavailable. Please try again later."
        } finally { loading = false }
    }

    GroupLabel("Pickup calls")
    SettingsCard {
        Text("Phone number", style = SaluText.FoodTitle, color = SaluColors.DarkGreen)
        Text(
            "Optional. Save a number so your pickup partner can reach you when chat is not enough.",
            style = SaluText.Supporting, color = SaluColors.Gray
        )
        if (loading) {
            CircularProgressIndicator(Modifier.size(24.dp), color = SaluColors.Green)
        } else if (!loaded) {
            TextButton(onClick = { retry++ }) { Text("Retry phone settings") }
        } else {
            OutlinedTextField(
                value = phone,
                onValueChange = { if (it.length <= 64) { phone = it; feedback = "" } },
                label = { Text("Mobile or phone number") },
                placeholder = { Text("09XXXXXXXXX or +63…") },
                supportingText = { Text("Philippine mobile: 09 + 9 digits. Other numbers: include + and country code.") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                singleLine = true,
                enabled = !saving,
                modifier = Modifier.fillMaxWidth(),
                shape = SaluRadius.Control,
                colors = saluFieldColors()
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Share my number for active pickups",
                    modifier = Modifier.weight(1f).padding(end = 12.dp),
                    style = SaluText.Body, color = SaluColors.DarkGreen
                )
                Switch(
                    checked = share,
                    onCheckedChange = { share = it; feedback = "" },
                    enabled = !saving,
                    modifier = Modifier.semantics { contentDescription = "Share my number for active pickups" }
                )
            }
            Text(
                "When enabled, the donor or recipient in an active reservation can see your number in their phone dialer. Turning this off prevents new access, but cannot remove a number they already saved. Your number is not verified by Salu-Salo.",
                style = SaluText.Supporting, color = SaluColors.Gray
            )
            SaluPrimaryButton(
                text = if (saving) "Saving…" else "Save phone settings",
                enabled = !saving,
                loading = saving,
                onClick = {
                    val normalized = normalizePickupPhoneNumber(phone)
                    when {
                        phone.isBlank() && share -> feedback = "Enter a phone number, or turn sharing off."
                        phone.isNotBlank() && normalized == null -> feedback = "Enter a valid 09 mobile number or a number with + and country code."
                        else -> {
                            saving = true
                            feedback = ""
                            scope.launch {
                                try {
                                    val saved = repository.saveMyContact(normalized.orEmpty(), share)
                                    phone = saved.phoneNumber.orEmpty()
                                    share = saved.sharePhoneForPickup
                                    feedback = "Phone settings saved."
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (_: Exception) {
                                    feedback = "Phone settings could not be saved. Please try again."
                                } finally { saving = false }
                            }
                        }
                    }
                }
            )
            Text("These settings save separately from your name and bio.", style = SaluText.Supporting, color = SaluColors.Gray)
        }
        if (feedback.isNotBlank()) InlineNotice(text = feedback, icon = Icons.Outlined.Info)
    }
}

@Composable
internal fun ChatCallButton(conversationId: String, otherName: String) {
    key(conversationId) { ChatCallButtonContent(conversationId, otherName) }
}

@Composable
private fun ChatCallButtonContent(conversationId: String, otherName: String) {
    val context = LocalContext.current
    val repository = remember { PhoneContactRepository() }
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }

    IconButton(
        enabled = !loading,
        onClick = {
            loading = true
            scope.launch {
                try {
                    val contact = repository.getCallContact(conversationId)
                    when (contact.status) {
                        "AVAILABLE" -> {
                            // Defence in depth: never pass arbitrary strings or service codes to tel:.
                            val phone = contact.phoneNumber?.let(::normalizePickupPhoneNumber)
                            if (phone == null) {
                                notice = "This person has not shared a valid phone number. Please use chat."
                            } else {
                                // Opens the dialer only. The user presses Call there; no CALL_PHONE permission.
                                context.startActivity(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", phone, null)))
                            }
                        }
                        "NO_ACTIVE_RESERVATION" -> notice = "Calls are available while you have an active reservation together. Completed, cancelled or expired pickups cannot use this option."
                        "NOT_SHARED" -> notice = "This person has not shared a phone number for pickups. Please use chat."
                        else -> notice = "Calling is unavailable. Please use chat."
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: ActivityNotFoundException) {
                    notice = "No phone dialer is available on this device. Please use chat."
                } catch (_: SecurityException) {
                    notice = "This device could not open the phone dialer. Please use chat."
                } catch (_: Exception) {
                    notice = "Could not check the contact number. Check your connection and try again."
                } finally { loading = false }
            }
        }
    ) {
        if (loading) CircularProgressIndicator(Modifier.size(20.dp), color = SaluColors.Green, strokeWidth = 2.dp)
        else Icon(Icons.Outlined.Call, contentDescription = "Call $otherName using phone dialer", tint = SaluColors.DarkGreen)
    }
    notice?.let { message ->
        AlertDialog(
            onDismissRequest = { notice = null },
            title = { Text("Pickup call") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { notice = null }) { Text("OK") } }
        )
    }
}
