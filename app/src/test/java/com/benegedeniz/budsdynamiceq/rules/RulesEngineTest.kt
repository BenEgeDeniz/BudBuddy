package com.benegedeniz.budsdynamiceq.rules

import com.benegedeniz.budsdynamiceq.data.model.EqPreset
import com.benegedeniz.budsdynamiceq.data.model.EqRule
import com.benegedeniz.budsdynamiceq.media.SongMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class RulesEngineTest {

    private lateinit var rulesEngine: RulesEngine

    @Before
    fun setup() {
        rulesEngine = RulesEngine()
    }

    @Test
    fun evaluate_matchFound_returnsRule() {
        val rules = listOf(
            EqRule(id = "1", keyword = "miles", preset = EqPreset.BASS_BOOST, priority = 1),
            EqRule(id = "2", keyword = "captain", preset = EqPreset.DYNAMIC, priority = 2)
        )
        val result = rulesEngine.evaluate(SongMetadata(title = "500 Miles Away From Home"), rules)
        assertEquals("1", result?.id)
        assertEquals(EqPreset.BASS_BOOST, result?.preset)
    }

    @Test
    fun evaluate_caseInsensitive_returnsRule() {
        val rules = listOf(
            EqRule(id = "1", keyword = "MILES", preset = EqPreset.BASS_BOOST, priority = 1)
        )
        val result = rulesEngine.evaluate(SongMetadata(title = "500 miles away from home"), rules)
        assertEquals("1", result?.id)
    }

    @Test
    fun evaluate_noMatch_returnsNull() {
        val rules = listOf(
            EqRule(id = "1", keyword = "captain", preset = EqPreset.DYNAMIC, priority = 1)
        )
        val result = rulesEngine.evaluate(SongMetadata(title = "500 Miles Away From Home"), rules)
        assertNull(result)
    }

    @Test
    fun evaluate_respectsPriority() {
        val rules = listOf(
            EqRule(id = "1", keyword = "home", preset = EqPreset.SOFT, priority = 1), // higher priority
            EqRule(id = "2", keyword = "miles", preset = EqPreset.BASS_BOOST, priority = 2)
        )
        val result = rulesEngine.evaluate(SongMetadata(title = "500 Miles Away From Home"), rules)
        assertEquals("1", result?.id)
    }

    @Test
    fun evaluate_disabledRule_ignored() {
        val rules = listOf(
            EqRule(id = "1", keyword = "miles", preset = EqPreset.BASS_BOOST, enabled = false, priority = 1),
            EqRule(id = "2", keyword = "home", preset = EqPreset.DYNAMIC, enabled = true, priority = 2)
        )
        val result = rulesEngine.evaluate(SongMetadata(title = "500 Miles Away From Home"), rules)
        assertEquals("2", result?.id)
    }
}
