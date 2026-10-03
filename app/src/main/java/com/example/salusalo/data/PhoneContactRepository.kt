package com.example.salusalo.data

import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Serializable
data class MyPhoneContact(
    @SerialName("phone_number") val phoneNumber: String? = null,
    @SerialName("share_phone_for_pickup") val sharePhoneForPickup: Boolean = false
)

@Serializable
data class PickupCallContact(
    val status: String,
    @SerialName("phone_number") val phoneNumber: String? = null
)

/** Phone numbers are never included in the inbox or public profile queries. */
class PhoneContactRepository {
    private val postgrest get() = SupabaseClientProvider.client.postgrest

    suspend fun getMyContact(): MyPhoneContact =
        postgrest.rpc("get_my_phone_contact").decodeList<MyPhoneContact>().single()

    suspend fun saveMyContact(phoneNumber: String, share: Boolean): MyPhoneContact =
        postgrest.rpc("update_my_phone_contact", buildJsonObject {
            put("p_phone_number", phoneNumber)
            put("p_share_phone_for_pickup", share)
        }).decodeList<MyPhoneContact>().single()

    /** Always check permission on the server immediately before opening the dialer. */
    suspend fun getCallContact(conversationId: String): PickupCallContact =
        postgrest.rpc("get_pickup_call_contact", buildJsonObject {
            put("p_conversation_id", conversationId)
        }).decodeList<PickupCallContact>().single()
}

/** Format validation only: this does not verify ownership or reachability. */
internal fun normalizePickupPhoneNumber(raw: String): String? {
    val input = raw.trim()
    if (input.isEmpty() || input.length > 64) return null
    if (!Regex("[+0-9 ()-]+").matches(input)) return null
    var number = input.replace(Regex("[ ()-]"), "")
    if (Regex("09[0-9]{9}").matches(number)) number = "+63" + number.drop(1)
    return number.takeIf { Regex("\\+[1-9][0-9]{7,14}").matches(it) }
}
