package nl.spellenweekend.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class GameFilterTest {
    private val catan = Game(id = "1", name = "Catan", owner = "Anna", minPlayers = 3, maxPlayers = 4, weight = Weight.MIDDEL, category = "Strategie")
    private val azul = Game(id = "2", name = "azul", owner = " bram ", minPlayers = 2, maxPlayers = 4, weight = Weight.LICHT)
    private val wish = Game(id = "3", name = "Brass", owner = "", wishlist = true, minPlayers = 2, maxPlayers = 4, weight = Weight.ZWAAR)
    // Wenslijst-spel met een verdwaalde eigenaar: telt niet als meegebracht
    private val staleWish = Game(id = "4", name = "Dune", owner = "Anna", wishlist = true, maxPlayers = 6)
    private val party = Game(id = "5", name = "Party 10", owner = "Bram", maxPlayers = 10)
    private val all = listOf(catan, azul, wish, staleWish, party)

    private fun ids(l: List<Game>) = l.map { it.id }

    @Test fun noFilterReturnsEverythingSortedCaseInsensitive() {
        assertEquals(listOf("2", "3", "1", "4", "5"), ids(filterGames(all)))
    }

    @Test fun anyoneKeepsOnlyBroughtGames() {
        assertEquals(listOf("2", "1", "5"), ids(filterGames(all, owner = ANYONE_OWNER)))
    }

    @Test fun blankOwnerDoesNotFilterWishlist() {
        assertEquals(5, filterGames(all, owner = "").size)
    }

    @Test fun ownerMatchesTrimmedAndCaseInsensitive() {
        assertEquals(listOf("2", "5"), ids(filterGames(all, owner = "BRAM")))
    }

    @Test fun goingAndWishlistListsAreDisjoint() {
        assertEquals(listOf("2", "1", "5"), ids(filterGames(all, list = LIST_GOING)))
        assertEquals(listOf("3", "4"), ids(filterGames(all, list = LIST_WISHLIST)))
    }

    @Test fun playersExactAndSevenPlus() {
        assertEquals(listOf("2", "3", "4", "5"), ids(filterGames(all, players = 2)))
        assertEquals(listOf("5"), ids(filterGames(all, players = 7)))
        assertEquals(listOf("2"), ids(filterGames(listOf(catan, azul), players = 2)))
        assertEquals(listOf("2", "1"), ids(filterGames(listOf(catan, azul), players = 3)))
    }

    @Test fun categoryBlankCountsAsOverig() {
        assertEquals(listOf("1"), ids(filterGames(all, category = "Strategie")))
        assertEquals(listOf("2", "3", "4", "5"), ids(filterGames(all, category = "Overig")))
    }

    @Test fun weightsQueryAndOnlyIdsCombine() {
        assertEquals(listOf("3"), ids(filterGames(all, weights = setOf("ZWAAR"))))
        assertEquals(listOf("2"), ids(filterGames(all, query = " AZ ")))
        assertEquals(listOf("5"), ids(filterGames(all, onlyIds = setOf("5", "3"), owner = ANYONE_OWNER)))
        assertEquals(emptyList<String>(), ids(filterGames(all, onlyIds = emptySet())))
    }
}
