package com.benegedeniz.budsdynamiceq.rules

import com.benegedeniz.budsdynamiceq.data.model.EqPreset
import com.benegedeniz.budsdynamiceq.data.model.EqRule

class RulesEngine {

    /**
     * Evaluates the current song metadata against the list of rules.
     * Returns a matching EqRule if a match is found.
     * Returns null if no match is found.
     */
    fun evaluate(metadata: com.benegedeniz.budsdynamiceq.media.SongMetadata?, rules: List<EqRule>): EqRule? {
        if (metadata == null) return null
        
        val titleLower = metadata.title?.lowercase() ?: ""
        val artistLower = metadata.artist?.lowercase() ?: ""
        val genreLower = metadata.genre?.lowercase() ?: ""

        val combinedSearchString = listOfNotNull(metadata.title, metadata.artist, metadata.genre)
            .joinToString(" ")
            .trim()
            .lowercase()

        if (combinedSearchString.isBlank()) return null

        // Find the first enabled rule whose keyword is contained within the song metadata
        // Rules are expected to be pre-sorted by priority
        return rules.firstOrNull { rule ->
            if (!rule.enabled || rule.keyword.isBlank()) return@firstOrNull false

            val keyword = rule.keyword.trim().lowercase()

            val titleTarget = Regex("title:(.*?)(?: artist:| genre:|$)").find(keyword)?.groupValues?.get(1)?.trim()
            val artistTarget = Regex("artist:(.*?)(?: title:| genre:|$)").find(keyword)?.groupValues?.get(1)?.trim()
            val genreTarget = Regex("genre:(.*?)(?: title:| artist:|$)").find(keyword)?.groupValues?.get(1)?.trim()

            if (titleTarget != null && (titleTarget.isEmpty() || !titleLower.contains(titleTarget))) return@firstOrNull false
            if (artistTarget != null && (artistTarget.isEmpty() || !artistLower.contains(artistTarget))) return@firstOrNull false
            if (genreTarget != null && (genreTarget.isEmpty() || !genreLower.contains(genreTarget))) return@firstOrNull false

            if (titleTarget == null && artistTarget == null && genreTarget == null) {
                if (!combinedSearchString.contains(keyword)) return@firstOrNull false
            }

            return@firstOrNull true
        }
    }
}
