package com.divya.ai

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Outfit dialog ki halat. Voice (change_outfit tool) aur chat dono yahin se dialog kholte hain. */
object OutfitBus {
    var open by mutableStateOf(false)
    var version by mutableIntStateOf(0)

    /** Har request par badhta hai. Dialog ko is key se dobara banate hain, taaki purani window ki atki hui halat na rahe. */
    var requests by mutableIntStateOf(0)

    /** Dialog kholo (tile, voice tool, chat - sab yahin se). */
    fun show() { requests++; open = true }

    /** Chat text mein "cloth/kapde/outfit badlo" jaisi baat hai? */
    fun isRequest(t: String): Boolean = Regex(
        "(cloth|clothes|kapde|kapda|outfit|dress|poshak|look).{0,20}(change|badal)|(change|badal).{0,20}(cloth|clothes|kapde|kapda|outfit|dress|poshak)",
        RegexOption.IGNORE_CASE
    ).containsMatchIn(t)
}
