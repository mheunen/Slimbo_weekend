@file:OptIn(ExperimentalLayoutApi::class)

package nl.spellenweekend.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.FilterChip
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import nl.spellenweekend.app.data.GAME_CATEGORIES
import nl.spellenweekend.app.data.Weight
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import nl.spellenweekend.app.data.Activity
import nl.spellenweekend.app.data.ActivityType
import nl.spellenweekend.app.data.Game
import nl.spellenweekend.app.data.GameTable
import nl.spellenweekend.app.data.Matchmaker
import nl.spellenweekend.app.data.Member
import nl.spellenweekend.app.data.MemberStatus

@Composable
fun MatchScreen(
    vm: AppViewModel,
    me: String,
    look: Lookup,
    members: List<Member>,
    games: List<Game>,
    tables: List<GameTable>,
    activities: List<Activity>,
    onEdit: (Activity) -> Unit,
) {
    // ---------- filters ----------
    var showFilters by rememberSaveable { mutableStateOf(false) }
    var withMe by rememberSaveable { mutableStateOf(false) }          // alleen spellen met mijn ♥
    var weights by rememberSaveable { mutableStateOf(setOf<String>()) }
    var category by rememberSaveable { mutableStateOf("") }
    var maxDuration by rememberSaveable { mutableIntStateOf(0) }     // 0 = alle
    var placeId by rememberSaveable { mutableStateOf("") }           // tafels in deze locatie
    val activeFilters = listOf(weights.isNotEmpty(), category.isNotBlank(), maxDuration != 0, placeId.isNotBlank()).count { it }
    val myself = look.members[me]
    val categories = remember(games) {
        games.filter { !it.wishlist }.map { it.category.ifBlank { "Overig" } }.distinct()
            .sortedBy { GAME_CATEGORIES.indexOf(it).let { i -> if (i < 0) 99 else i } }
    }
    val places = remember(look) { look.places.values.sortedBy { it.name.lowercase() } }

    val myWants = myself?.wants?.toSet().orEmpty()
    val filteredGames = games
        .filter { !withMe || it.id in myWants }
        .filter { weights.isEmpty() || it.weight.name in weights }
        .filter { category.isBlank() || it.category.ifBlank { "Overig" } == category }
        .filter { maxDuration == 0 || it.durationMin <= maxDuration }
    val filteredTables = tables.filter { placeId.isBlank() || it.placeId == placeId }
    val result = remember(filteredGames, members, filteredTables, activities) {
        Matchmaker.run(filteredGames, members, filteredTables, activities)
    }

    fun plan(game: Game, players: List<Member>, table: GameTable?) {
        onEdit(
            Activity(
                type = ActivityType.SPEL,
                title = game.name,
                gameId = game.id,
                startAt = roundedNow(),
                durationMin = game.durationMin,
                maxParticipants = game.maxPlayers,
                tableId = table?.id ?: "",
                placeId = table?.placeId ?: "",
                invited = players.map { it.id }.filter { it != me },
            )
        )
    }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text(
                "Op basis van ieders ♥-spellen, status (beschikbaar / reserveerbaar) en vrije tafels.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = withMe,
                    onClick = { withMe = !withMe },
                    label = { Text("Met mijn ♥ (${myWants.size})") },
                )
                FilterChip(
                    selected = showFilters || activeFilters > 0,
                    onClick = { showFilters = !showFilters },
                    label = { Text(if (activeFilters > 0) "Filters ($activeFilters)" else "Filters") },
                    leadingIcon = { Icon(Icons.Filled.FilterList, null) },
                )
            }
            if (showFilters) {
                Card(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Moeilijkheid", style = MaterialTheme.typography.labelLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Weight.entries.forEach { w ->
                                FilterChip(
                                    selected = w.name in weights,
                                    onClick = { weights = if (w.name in weights) weights - w.name else weights + w.name },
                                    label = { Text(w.label) },
                                )
                            }
                        }
                        Text("Speelduur", style = MaterialTheme.typography.labelLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(0, 30, 60, 90, 120).forEach { d ->
                                FilterChip(
                                    selected = maxDuration == d,
                                    onClick = { maxDuration = d },
                                    label = { Text(if (d == 0) "Alle" else "≤ $d min") },
                                )
                            }
                        }
                        SelectField(
                            label = "Categorie",
                            value = category,
                            options = listOf("" to "Alle categorieën") + categories.map { it to it },
                            onSelect = { category = it },
                        )
                        if (places.isNotEmpty()) {
                            SelectField(
                                label = "Tafel in locatie",
                                value = placeId,
                                options = listOf("" to "Alle locaties") + places.map { it.id to it.name },
                                onSelect = { placeId = it },
                            )
                        }
                        if (activeFilters > 0) {
                            TextButton(onClick = {
                                weights = emptySet(); category = ""; maxDuration = 0; placeId = ""
                            }) { Text("Filters wissen") }
                        }
                    }
                }
            }
        }
        if (myself?.status == MemberStatus.BEZET) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                    Text(
                        "Je staat op Bezet, dus je telt nu niet mee in de suggesties.",
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
        }

        item { SectionHeader("Kan nu gespeeld worden") }
        if (result.suggestions.isEmpty()) {
            item {
                EmptyHint(
                    if (members.all { it.wants.isEmpty() })
                        "Nog niemand heeft spellen met ♥ gemarkeerd. Doe dat op het tabblad Spellen."
                    else if (withMe || activeFilters > 0) "Geen spel past bij deze filters. Pas ze aan of wis ze."
                    else "Geen spel heeft nu genoeg vrije liefhebbers."
                )
            }
        }
        items(result.suggestions, key = { "s-" + it.game.id }) { s ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(s.game.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${s.players.size} van max. ${s.game.maxPlayers} · ${s.game.durationMin} min · " +
                            (s.table?.let { t ->
                                "vrij: ${t.name}" + (look.places[t.placeId]?.name?.let { " (${it})" } ?: "")
                            } ?: "geen vrije tafel"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(vertical = 8.dp),
                    ) {
                        s.players.forEach { MemberPill(it) }
                    }
                    Button(onClick = { plan(s.game, s.players, s.table) }) {
                        Icon(Icons.Filled.EventAvailable, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Plan en nodig uit")
                    }
                }
            }
        }

        if (result.nearMisses.isNotEmpty()) {
            item { SectionHeader("Bijna — er mist nog iemand") }
            items(result.nearMisses, key = { "n-" + it.game.id }) { n ->
                val iWant = myself?.wants?.contains(n.game.id) == true
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(n.game.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Nog ${n.missing} speler${if (n.missing > 1) "s" else ""} nodig · " +
                                n.interested.joinToString(", ") { it.name },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (!iWant) {
                            OutlinedButton(
                                onClick = { vm.setWant(n.game.id, true) },
                                modifier = Modifier.padding(top = 8.dp),
                            ) {
                                Icon(Icons.Filled.Favorite, null)
                                Spacer(Modifier.width(6.dp))
                                Text("Ik wil ook")
                            }
                        }
                    }
                }
            }
        }

        item { SectionHeader("Vrije tafels") }
        if (tables.isEmpty()) {
            item { EmptyHint("Nog geen tafels. Voeg ze toe onder Meer.") }
        } else if (result.freeTables.isEmpty()) {
            item { EmptyHint("Alle tafels zijn bezet of komend uur gereserveerd.") }
        } else {
            item {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    result.freeTables.forEach { t ->
                        Card {
                            Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                Text(
                                    "${t.name} · ${t.seats} pl." +
                                        (look.places[t.placeId]?.name?.let { " · $it" } ?: ""),
                                )
                            }
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.padding(bottom = 24.dp)) }
    }
}
