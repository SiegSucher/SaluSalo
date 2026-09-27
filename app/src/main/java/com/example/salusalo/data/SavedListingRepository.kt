package com.example.salusalo.data

import com.example.salusalo.model.NewSavedListing
import com.example.salusalo.model.SavedListingName
import com.example.salusalo.model.SavedListingRow
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.storage.storage
import kotlin.coroutines.cancellation.CancellationException

/**
 * Bookmarks in saved_listings. RLS limits every call to the caller's own rows,
 * and lets them save only a listing they can see that is not their own.
 */
class SavedListingRepository(
    private val userRepository: UserRepository = UserRepository(),
    private val foodRepository: FoodRepository = FoodRepository()
) {
    private val supabase = SupabaseClientProvider.client
    private val bucket = supabase.storage.from("food-images")

    /** The caller's saved rows, newest saved first, with each visible listing, its donor's name and rating, and its cover URL. */
    suspend fun getSavedListings(): List<SavedListingRow> {
        val rows = supabase.from("saved_listings").select(Columns.raw("food_post_id, created_at, food_posts(*)")) {
            order("created_at", Order.DESCENDING)
        }.decodeList<SavedListingRow>()
        val named = foodRepository.withDonorProfiles(rows.mapNotNull { it.foodPost }).associateBy { it.id }
        val hidden = if (rows.any { it.foodPost == null }) savedNames() else emptyMap()
        return rows.map { row ->
            val post = named[row.foodPostId]
            if (post != null) {
                row.copy(foodPost = post.withSignedImageUrl(bucket))
            } else {
                val name = hidden[row.foodPostId]
                row.copy(
                    fallbackName = name?.name,
                    fallbackImageUrl = bucket.signedUrlOrNull(name?.imagePath)
                )
            }
        }
    }

    /**
     * Name and cover path of every saved listing, from get_my_saved_listing_names(),
     * for those the caller can no longer see. If it fails, those cards stay blank.
     */
    private suspend fun savedNames(): Map<String, SavedListingName> = try {
        supabase.postgrest.rpc("get_my_saved_listing_names")
            .decodeList<SavedListingName>().associateBy { it.foodPostId }
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        emptyMap()
    }

    suspend fun save(foodPostId: String) {
        val profile = userRepository.getCurrentProfile()
        // A second tap on another phone is not an error: the row already exists.
        supabase.from("saved_listings").upsert(NewSavedListing(profile.id, foodPostId)) {
            ignoreDuplicates = true
        }
    }

    suspend fun remove(foodPostId: String) {
        supabase.from("saved_listings").delete {
            filter { filter("food_post_id", FilterOperator.EQ, foodPostId) }
        }
    }
}
