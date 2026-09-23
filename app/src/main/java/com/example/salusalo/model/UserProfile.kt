package com.example.salusalo.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserProfile(
    val id: String,
    @SerialName("firebase_uid")
    val firebaseUid: String,
    val name: String,
    val email: String,
    val role: UserRole,
    @SerialName("profile_image_url")
    val profileImageUrl: String? = null,
    @SerialName("phone_number")
    val phoneNumber: String? = null,
    @SerialName("created_at")
    val createdAt: String? = null,
    @SerialName("updated_at")
    val updatedAt: String? = null
)

@Serializable
enum class UserRole {
    DONOR,
    RECIPIENT,
    ADMIN
}

/*
 * This payload intentionally has no id or role. The database supplies the
 * identifier and defaults every new client-created profile to RECIPIENT.
 */
@Serializable
data class NewUserProfile(
    @SerialName("firebase_uid")
    val firebaseUid: String,
    val name: String,
    val email: String
)