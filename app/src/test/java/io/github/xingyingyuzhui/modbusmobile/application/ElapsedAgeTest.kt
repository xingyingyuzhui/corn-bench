package io.github.xingyingyuzhui.modbusmobile.application

import org.junit.Assert.assertEquals
import org.junit.Test

class ElapsedAgeTest {
    @Test
    fun picksTheLargestUnitThatStaysAtLeastOne() {
        assertEquals(ElapsedAge(0, AgeUnit.SECONDS), ElapsedAge.of(999))
        assertEquals(ElapsedAge(45, AgeUnit.SECONDS), ElapsedAge.of(45_000))
        assertEquals(ElapsedAge(1, AgeUnit.MINUTES), ElapsedAge.of(74_000))
        assertEquals(ElapsedAge(59, AgeUnit.SECONDS), ElapsedAge.of(59_999))
        assertEquals(ElapsedAge(1, AgeUnit.MINUTES), ElapsedAge.of(60_000))
        assertEquals(ElapsedAge(59, AgeUnit.MINUTES), ElapsedAge.of(3_599_999))
        assertEquals(ElapsedAge(1, AgeUnit.HOURS), ElapsedAge.of(3_600_000))
        assertEquals(ElapsedAge(23, AgeUnit.HOURS), ElapsedAge.of(86_399_999))
        // 截图里出现过的 591152 秒 ≈ 6.8 天
        assertEquals(ElapsedAge(6, AgeUnit.DAYS), ElapsedAge.of(591_152_000))
    }

    @Test
    fun clockGoingBackwardsReadsAsZeroSeconds() {
        assertEquals(ElapsedAge(0, AgeUnit.SECONDS), ElapsedAge.of(-5_000))
    }
}
