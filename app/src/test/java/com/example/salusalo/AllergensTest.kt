package com.example.salusalo

import com.example.salusalo.model.Allergen
import com.example.salusalo.model.ListingAllergens
import com.example.salusalo.model.allergenNames
import com.example.salusalo.model.allergenText
import com.example.salusalo.model.allergiesIn
import com.example.salusalo.model.parseAllergens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** My allergies hides only what the donor picked; typed text is never matched, so never called safe. */
class AllergensTest {
    @Test
    fun theChipsAreStoredAsReadableText() {
        assertEquals("None", allergenText(emptySet(), null))
        assertEquals("Milk, Shellfish", allergenText(setOf(Allergen.SHELLFISH, Allergen.MILK), null))
        assertEquals("Eggs, Other: crab paste, annatto", allergenText(setOf(Allergen.EGGS), " crab paste, annatto "))
        assertEquals("Other: crab paste", allergenText(emptySet(), "crab paste"))
        assertEquals("Milk", allergenText(setOf(Allergen.MILK), "  "))
    }

    @Test
    fun storedTextReadsBackAsTheSameChips() {
        val picks = listOf(
            emptySet<Allergen>() to null,
            setOf(Allergen.MILK, Allergen.WHEAT) to null,
            Allergen.entries.toSet() to "crab paste, annatto",
            emptySet<Allergen>() to "crab paste"
        )
        picks.forEach { (picked, other) ->
            assertEquals(ListingAllergens(picked, other), parseAllergens(allergenText(picked, other)))
        }
        assertEquals(ListingAllergens(setOf(Allergen.MILK, Allergen.EGGS), null), parseAllergens(" milk ,EGGS "))
    }

    @Test
    fun blankOrTypedTextCannotBeChecked() {
        listOf(
            null, "", "  ", "Contains gatas", "Milk and eggs", "None.", "None, but made near nuts",
            "Nuts", "Other:", "MilkOther: crab", ","
        ).forEach { assertNull(it, parseAllergens(it)) }
    }

    @Test
    fun onlyThePersonsAllergiesThatTheListingPickedMatch() {
        val mine = setOf(Allergen.SHELLFISH, Allergen.MILK)
        assertEquals(listOf(Allergen.MILK, Allergen.SHELLFISH), allergiesIn("Shellfish, Eggs, Milk", mine))
        assertTrue(allergiesIn("Eggs, Other: shrimp paste", mine).isEmpty())
        assertTrue(allergiesIn("None", mine).isEmpty())
        // Typed text mentioning milk can't be checked: it is shown, with a note, not hidden.
        assertTrue(allergiesIn("Contains milk", mine).isEmpty())
        assertTrue(allergiesIn("Milk", emptySet()).isEmpty())
    }

    @Test
    fun namesReadAsASentence() {
        assertEquals("Milk", allergenNames(listOf(Allergen.MILK)))
        assertEquals("Milk and Eggs", allergenNames(listOf(Allergen.MILK, Allergen.EGGS)))
        assertEquals("Milk, Eggs and Fish", allergenNames(listOf(Allergen.MILK, Allergen.EGGS, Allergen.FISH)))
    }
}
