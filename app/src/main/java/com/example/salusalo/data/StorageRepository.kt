package com.example.salusalo.data

/**
 * Centralizes the private Storage paths that later upload features must use.
 * Keeping Firebase UIDs as the first folder prevents ambiguous ownership.
 */
class StorageRepository {
    // Each photo gets a new name: overwriting one path can leave the storage
    // CDN serving the old picture for a while.
    fun profileImagePath(
        firebaseUid: String,
        uploadedAtMillis: Long = System.currentTimeMillis()
    ): String = "$firebaseUid/profile-$uploadedAtMillis.jpg"

    fun foodImagePath(
        firebaseUid: String,
        postId: String,
        fileName: String
    ): String = "$firebaseUid/$postId/$fileName"
}