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
    private val userRepository: UserRepository = UserRepository()
) {
    private val supabase = SupabaseClientProvider.client
    private val postgrest = supabase.postgrest
    private val bucket = supabase.storage.from("food-images")

    suspend fun createReservation(foodPostId: String, quantity: Int): Reservation {
        require(quantity > 0) { "Choose a quantity greater than zero." }
        return postgrest.rpc("create_reservation", buildJsonObject {
            put("p_food_post_id", foodPostId); put("p_quantity", quantity)
        }).decodeAs()
    }

    suspend fun cancelReservation(reservationId: String): Reservation =
        postgrest.rpc("cancel_reservation", buildJsonObject { put("p_reservation_id", reservationId) }).decodeAs()

    suspend fun claimReservation(reservationId: String): Reservation =
        postgrest.rpc("claim_reservation", buildJsonObject { put("p_reservation_id", reservationId) }).decodeAs()

    suspend fun getMyReservations(): List<ReservationDisplay> = getReservationsForProfile(userRepository.getCurrentProfile().id)

    suspend fun getReservationsForMyFoodPosts(): List<ReservationDisplay> {
        // This RPC exposes recipient names only for reservations on the signed-in
        // donor's own food posts; it does not broaden profile-table RLS access.
        val rows = postgrest.rpc("get_donor_reservations").decodeList<DonorReservationRow>()
        if (rows.isEmpty()) return emptyList()
        val ids = rows.map { it.foodPostId }.toSet()
        val posts = supabase.from("food_posts").select().decodeList<FoodPost>()
            .filter { it.id in ids }.map { it.withSignedImageUrl(bucket) }.associateBy { it.id }
        return rows.mapNotNull { row ->
            posts[row.foodPostId]?.let { post ->
                ReservationDisplay(row.toReservation(), post, row.recipientName)
            }
        }
    }

    private suspend fun getReservationsForProfile(profileId: String): List<ReservationDisplay> =
        supabase.from("reservations").select { filter { filter("recipient_id", FilterOperator.EQ, profileId) } }
            .decodeList<Reservation>().toDisplays()

    private suspend fun List<Reservation>.toDisplays(): List<ReservationDisplay> {
        if (isEmpty()) return emptyList()
        val ids = map { it.foodPostId }.toSet()
        val posts = supabase.from("food_posts").select().decodeList<FoodPost>()
            .filter { it.id in ids }.map { it.withSignedImageUrl(bucket) }.associateBy { it.id }
        return mapNotNull { reservation -> posts[reservation.foodPostId]?.let { ReservationDisplay(reservation, it) } }
    }
}



