package nl.spellenweekend.app.data

data class Suggestion(
    val game: Game,
    val players: List<Member>,
    val table: GameTable?,
    val score: Double,
)

data class NearMiss(
    val game: Game,
    val interested: List<Member>,
    val missing: Int,
)

data class MatchResult(
    val suggestions: List<Suggestion>,
    val nearMisses: List<NearMiss>,
    val freeTables: List<GameTable>,
)

/**
 * Eenvoudige matchmaking:
 * - Kandidaten = leden die het spel op hun "wil ik spelen"-lijst hebben,
 *   niet op "Bezet" staan en niet in een lopende activiteit zitten.
 * - "Beschikbaar" gaat voor "Reserveerbaar".
 * - Een spel komt in aanmerking als het minimum aantal spelers gehaald wordt.
 * - Score: hoe voller het spel (t.o.v. max), hoe meer direct beschikbare spelers
 *   en of er een vrije tafel met genoeg plaatsen is.
 * - Tafels gelden als bezet als er een activiteit loopt of binnen het komende uur gepland staat.
 */
object Matchmaker {

    fun run(
        games: List<Game>,
        members: List<Member>,
        tables: List<GameTable>,
        activities: List<Activity>,
        now: Long = System.currentTimeMillis(),
    ): MatchResult {
        val running = activities.filter { it.status == ActivityStatus.GESTART }
        val busyIds = running.flatMap { it.participants }.toSet()
        val soon = activities.filter {
            it.status == ActivityStatus.GEPLAND &&
                it.startAt in (now - 15 * 60_000L)..(now + 60 * 60_000L)
        }
        val occupied = (running + soon).map { it.tableId }.filter { it.isNotBlank() }.toSet()
        val freeTables = tables.filter { it.id !in occupied }

        val suggestions = mutableListOf<Suggestion>()
        val nearMisses = mutableListOf<NearMiss>()

        // Spellen van de wenslijst neemt (nog) niemand mee
        for (g in games.filter { !it.wishlist }) {
            val interested = members
                .filter { g.id in it.wants && it.status != MemberStatus.BEZET && it.id !in busyIds }
                .sortedBy { if (it.status == MemberStatus.BESCHIKBAAR) 0 else 1 }

            if (interested.size >= g.minPlayers) {
                val players = interested.take(g.maxPlayers.coerceAtLeast(g.minPlayers))
                val table = freeTables
                    .filter { it.seats >= players.size }
                    .minByOrNull { it.seats }
                val fill = players.size.toDouble() / g.maxPlayers.coerceAtLeast(1)
                val ready = players.count { it.status == MemberStatus.BESCHIKBAAR }
                    .toDouble() / players.size
                val score = fill * 0.6 + ready * 0.3 + (if (table != null) 0.1 else 0.0)
                suggestions += Suggestion(g, players, table, score)
            } else if (interested.isNotEmpty() && g.minPlayers - interested.size <= 2) {
                nearMisses += NearMiss(g, interested, g.minPlayers - interested.size)
            }
        }

        return MatchResult(
            suggestions = suggestions.sortedByDescending { it.score },
            nearMisses = nearMisses.sortedBy { it.missing },
            freeTables = freeTables,
        )
    }
}
