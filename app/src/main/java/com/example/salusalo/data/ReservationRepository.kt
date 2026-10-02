package com.example.salusalo.data

import com.example.salusalo.model.FoodPost
import com.example.salusalo.model.DonorReservationRow
import com.example.salusalo.model.Reservation
import com.example.salusalo.model.ReservationDisplay
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.storage.storage
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class ReservationRepository(
    private val userRepository: UserRepository = UserRepository(),
    private val foodRepository: FoodRepository = FoodRepository(userRepository)
) {
    private val supabase = SupabaseClientProvider.client
    private val postgrest = supabase.postgrest
    private val bucket = supabase.storage.from("food-images")

    suspend fun createReservation(
        foodPostId: String,
        quantity: Int,
        pickupMinutes: Int
    ): Reservation {
        require(quantity > 0) { "Choose a quantity greater than zero." }
        require(pickupMinutes in setOf(15, 30, 60, 120)) { "Choose a valid pickup duration." }
        return postgrest.rpc("create_reservation_with_pickup", buildJsonObject {
            put("p_food_post_id", foodPostId)
            put("p_quantity", quantity)
            put("p_pickup_minutes", pickupMinutes)
        }).decodeAs()
    }

    suspend fun getReservationAllowance(foodPostId: String): com.example.salusalo.model.ReservationAllowance =
        postgrest.rpc("get_my_reservation_allowance", buildJsonObject {
            put("p_food_post_id", foodPostId)
        }).decodeList<com.example.salusalo.model.ReservationAllowance>().single()

    suspend fun cancelReservation(reservationId: String): Reservation =
        postgrest.rpc("cancel_reservation", buildJsonObject { put("p_reservation_id", reservationId) }).decodeAs()

    suspend fun claimReservation(reservationId: String): Reservation =
        postgrest.rpc("claim_reservation", buildJsonObject { put("p_reservation_id", reservationId) }).decodeAs()

    suspend fun getMyReservations(): List<ReservationDisplay> = getReservationsForProfile(userRepository.getCurrentProfile().id)

    suspend fun getReservationsForMyFoodPosts(): List<ReservationDisplay> {
        // This RPC exposes recipient names only for reservations on the signed-in
        // donor's own food posts; it does not broaden profile-table RLS access.
        val rows = postgrest.rpc("get_donor_reservations_with_pickup").decodeList<DonorReservationRow>()
        if (rows.isEmpty()) return emptyList()
        val posts = foodPostsById(rows.map { it.foodPostId }).withSignedImageUrls(bucket).associateBy { it.id }
        return rows.mapNotNull { row ->
            posts[row.foodPostId]?.let { post ->
                ReservationDisplay(row.toReservation(), post, row.recipientName)
            }
        }
    }

    private suspend fun getReservationsForProfile(profileId: String): List<ReservationDisplay> =
        supabase.from("reservations").select { filter { filter("recipient_id", FilterOperator.EQ, profileId) } }
            .decodeList<Reservation>().toDisplays()

    // The recipient's listings also get their donor's name ("Rate <name>").
    private suspend fun List<Reservation>.toDisplays(): List<ReservationDisplay> {
        if (isEmpty()) return emptyList()
        val reserved = foodPostsById(map { it.foodPostId })
        val posts = foodRepository.withDonorProfiles(reserved)
            .withSignedImageUrls(bucket).associateBy { it.id }
        return mapNotNull { reservation -> posts[reservation.foodPostId]?.let { ReservationDisplay(reservation, it) } }
    }

    /**
     * Only the listings with these IDs, as far as the caller may see them: the
     * food_posts SELECT policy decides, as for any read, so removed and expired
     * listings of the caller's own reservations still load. At most 100 IDs per
     * request keeps the URL short.
     */
    private suspend fun foodPostsById(ids: Collection<String>): List<FoodPost> =
        ids.distinct().chunked(MAX_IDS_PER_REQUEST).flatMap { chunk ->
            supabase.from("food_posts").select { filter { isIn("id", chunk) } }.decodeList<FoodPost>()
        }

    private companion object {
        const val MAX_IDS_PER_REQUEST = 100
    }
}



