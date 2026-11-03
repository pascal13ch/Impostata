package com.freeze.impostata.model

enum class GamePhase { SETUP, PLAYING, VOTING, RESULT }
enum class RevealMode { CLICK_TO_FLIP, HOLD_TO_REVEAL, GRID_SELECTION }

enum class WordCategory(val id: String, val displayName: String) {
    STANDARD("standard", "Standard"),
    COUNTRIES("countries", "Länder"),
    TECHNOLOGY("technology", "Technologie"),
    ANIMALS("animals", "Tiere"),
    FOOD("food", "Essen & Trinken"),
    SPORTS("sports", "Sport"),
    JOBS("jobs", "Berufe");

    // shown in the dropdown
    override fun toString() = displayName

    companion object {
        fun fromId(id: String?) = entries.firstOrNull { it.id == id } ?: STANDARD
        fun fromDisplayName(displayName: String?) = entries.firstOrNull() { it.displayName == displayName } ?: STANDARD
    }
}
