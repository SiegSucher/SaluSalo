package com.example.salusalo

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.example.salusalo.model.Allergen
import com.example.salusalo.model.FoodPost
import com.example.salusalo.model.allergiesIn

/** Device-local discovery choices, isolated by signed-in account. */
internal data class DiscoveryPreferences(
    val categories: Set<String> = emptySet(),
    val listingType: String = "Both",
    val area: String = "",
    // My allergies: Home leaves out listings whose donor picked one of them.
    // Not a food preference, so not part of isActive.
    val allergies: Set<Allergen> = emptySet()
) {
    val isActive: Boolean get() = categories.isNotEmpty() || listingType != "Both" || area.isNotBlank()
    fun myAllergiesIn(post: FoodPost): List<Allergen> = allergiesIn(post.allergens, allergies)
    fun matches(post: FoodPost): Boolean =
        (categories.isEmpty() || categories.any { it.equals(post.category, ignoreCase = true) }) &&
                (listingType == "Both" || (listingType == "Donations" && post.price == 0.0) ||
                        (listingType == "For sale" && post.price > 0.0)) &&
                (area.isBlank() || post.pickupLocation.contains(area.trim(), ignoreCase = true))
}

private fun discoveryStore(context: Context, accountId: String): SharedPreferences =
    context.getSharedPreferences("salusalo_discovery_$accountId", Context.MODE_PRIVATE)

private fun readDiscovery(store: SharedPreferences) = DiscoveryPreferences(
    categories = store.getStringSet("categories", emptySet())?.toSet().orEmpty(),
    listingType = store.getString("listing_type", "Both") ?: "Both",
    area = store.getString("area", "").orEmpty(),
    // Stored by name; a name this build doesn't know is skipped.
    allergies = store.getStringSet("allergies", emptySet()).orEmpty()
        .mapNotNull { name -> Allergen.entries.firstOrNull { it.name == name } }.toSet()
)

@Composable
internal fun rememberDiscoveryPreferences(accountId: String): DiscoveryPreferences {
    val context = LocalContext.current
    val store = remember(accountId, context) { discoveryStore(context, accountId) }
    var state by remember(store) { mutableStateOf(readDiscovery(store)) }
    DisposableEffect(store) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            state = readDiscovery(store)
        }
        store.registerOnSharedPreferenceChangeListener(listener)
        state = readDiscovery(store)
        onDispose { store.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return state
}

internal fun saveFoodPreferences(context: Context, accountId: String, categories: Set<String>, type: String) {
    discoveryStore(context, accountId).edit()
        .putStringSet("categories", categories.toSet()).putString("listing_type", type).apply()
}

internal fun saveAreaPreference(context: Context, accountId: String, area: String) {
    discoveryStore(context, accountId).edit().putString("area", area.trim()).apply()
}

internal fun saveAllergies(context: Context, accountId: String, allergies: Set<Allergen>) {
    discoveryStore(context, accountId).edit().putStringSet("allergies", allergies.map { it.name }.toSet()).apply()
}
