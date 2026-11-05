package nl.spellenweekend.app.data

/** Waarde van het eigenaarfilter voor "alle meegebrachte spellen" (dus zonder wenslijst). */
const val ANYONE_OWNER = "\u0000anyone"

/** Waarden van [filterGames] voor de lijstkeuze. */
const val LIST_ALL = 0
const val LIST_GOING = 1
const val LIST_WISHLIST = 2

/**
 * Filtert en sorteert de spellenlijst. Een leeg/nul filter doet niets.
 * [onlyIds] beperkt tot die spel-id's (Mijn ♥), null = geen beperking.
 * [owner]: "" = iedereen/geen filter, [ANYONE_OWNER] = alles wat iemand meeneemt, anders een naam.
 */
fun filterGames(
    games: List<Game>,
    query: String = "",
    onlyIds: Set<String>? = null,
    list: Int = LIST_ALL,
    players: Int = 0,
    weights: Set<String> = emptySet(),
    category: String = "",
    owner: String = "",
): List<Game> = games
    .filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
    .filter { onlyIds == null || it.id in onlyIds }
    .filter { list == LIST_ALL || (list == LIST_GOING && !it.wishlist) || (list == LIST_WISHLIST && it.wishlist) }
    .filter { players == 0 || (if (players >= 7) it.maxPlayers >= 7 else it.supports(players)) }
    .filter { weights.isEmpty() || it.weight.name in weights }
    .filter { category.isBlank() || it.category.ifBlank { "Overig" } == category }
    .filter {
        owner.isBlank() ||
            if (owner == ANYONE_OWNER) !it.wishlist && it.owner.isNotBlank()
            else it.owner.trim().equals(owner, ignoreCase = true)
    }
    .sortedBy { it.name.lowercase() }
