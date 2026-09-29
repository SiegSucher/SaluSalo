package com.example.salusalo

import com.example.salusalo.data.photoSampleSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Photos are sampled on their longer side before decoding, and never below the target size. */
class PhotoSampleTest {
    private val listing = 1600
    private val profile = 512

    @Test
    fun cameraPhotosAreSampledBeforeDecoding() {
        assertEquals(2, photoSampleSize(4032, 3024, listing)) // 12 MP landscape: 2016×1512
        assertEquals(2, photoSampleSize(3024, 4032, listing)) // 12 MP portrait
        assertEquals(4, photoSampleSize(8000, 6000, listing)) // 48 MP: 2000×1500
        assertEquals(4, photoSampleSize(12000, 2000, listing)) // panorama: 3000×500
        assertEquals(4, photoSampleSize(4032, 3024, profile)) // 1008×756 for a 512 px profile photo
    }

    @Test
    fun smallPhotosAreDecodedAsTheyAre() {
        assertEquals(1, photoSampleSize(1600, 1200, listing))
        assertEquals(1, photoSampleSize(3199, 2400, listing)) // half would be under 1600
        assertEquals(1, photoSampleSize(800, 600, listing))
    }

    @Test
    fun theDecodedLongerSideNeverFallsBelowTheTarget() {
        for ((w, h) in listOf(4032 to 3024, 8000 to 6000, 12000 to 9000, 2000 to 2000, 5000 to 100)) {
            val sample = photoSampleSize(w, h, listing)
            assertTrue("$w×$h at $sample", maxOf(w, h) / sample >= listing)
            assertTrue("$w×$h at $sample could sample more", maxOf(w, h) / (sample * 2) < listing)
        }
    }
}
