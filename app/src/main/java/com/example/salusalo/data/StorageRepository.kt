package com.example.salusalo.data

/**
 * Centralizes the private Storage paths that later upload features must use.
 * Keeping Firebase UIDs as the first folder prevents ambiguous ownership.
 */
class StorageRepository {
    fun profileImagePath(firebaseUid: String): String =
        "$firebaseUid/profile.jpg"

    fun foodImagePath(
        firebaseUid: String,
        postId: String,
        fileName: String
    ): String = "$firebaseUid/$postId/$fileName"
}