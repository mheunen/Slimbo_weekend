@file:OptIn(ExperimentalLayoutApi::class)

package nl.spellenweekend.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import android.content.Intent
import android.net.Uri
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.Checkbox
import androidx.compose.material3.IconButton
import androidx.compose.ui.platform.LocalContext
import nl.spellenweekend.app.data.Game
import nl.spellenweekend.app.data.GAME_CATEGORIES
import androidx.compose.material.icons.filled.FilterList
import nl.spellenweekend.app.data.Guest
import nl.spellenweekend.app.data.Member
import nl.spellenweekend.app.data.Weight

@Composable
fun GamesScreen(
    vm: AppViewModel,
    me: String,
    members: List<Member>,
    games: List<Game>,
    guests: List<Guest>,
) {
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    var onlyWanted by rememberSaveable { mutableStateOf(false) }
    var filter by rememberSaveable { mutableIntStateOf(0) } // 0 alles, 1 meegebracht, 2 wenslijst
    var players by rememberSaveable { mutableIntStateOf(0) } // 0 = alle
    var weights by rememberSaveable { mutableStateOf(setOf<String>()) }
    var category by rememberSaveable { mutableStateOf("") }
    var owner by rememberSaveable { mutableStateOf("") } // "" = iedereen
    var showFilters by rememberSaveable { mutableStateOf(false) }
    val myName = remember(members, guests) {
        val m = members.firstOrNull { it.id == me }
        guests.firstOrNull { it.id == m?.guestId }?.name ?: m?.name ?: ""
    }
    val owners = remember(games) {
        games.filter { !it.wishlist && it.owner.isNotBlank() }.map { it.owner.trim() }
            .distinctBy { it.lowercase() }.sortedBy { it.lowercase() }
    }
    val categories = remember(games) {
        games.map { it.category.ifBlank { "Overig" } }.distinct()
            .sortedBy { GAME_CATEGORIES.indexOf(it).let { i -> if (i < 0) 99 else i } }
    }
    val activeFilters = listOf(players != 0, weights.isNotEmpty(), category.isNotBlank(), owner.isNotBlank()).count { it }
    // Namen die al via de app meedoen: hun ♥ komt uit de app, niet meer uit de spreadsheet
    val claimedNames = remember(guests) { guests.filter { it.claimedBy.isNotBlank() }.map { it.name }.toSet() }
    var editing by remember { mutableStateOf<Game?>(null) }

    val myWants = members.firstOrNull { it.id == me }?.wants?.toSet() ?: emptySet()
    val wantCount: Map<String, List<Member>> = remember(members) {
        members.flatMap { m -> m.wants.map { it to m } }
            .groupBy({ it.first }, { it.second })
    }
    val visible = games
        .filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
        .filter { !onlyWanted || it.id in myWants }
        .filter { filter == 0 || (filter == 1 && !it.wishlist) || (filter == 2 && it.wishlist) }
        .filter { players == 0 || (if (players >= 7) it.maxPlayers >= 7 else it.supports(players)) }
        .filter { weights.isEmpty() || it.weight.name in weights }
        .filter { category.isBlank() || it.category.ifBlank { "Overig" } == category }
        .filter { owner.isBlank() || it.owner.trim().equals(owner, ignoreCase = true) }
        .sortedBy { it.name.lowercase() }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Zoek een spel") },
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (myName.isNotBlank()) {
                        FilterChip(
                            selected = owner.equals(myName, ignoreCase = true),
                            onClick = { owner = if (owner.equals(myName, ignoreCase = true)) "" else myName },
                            label = { Text("Neem ik mee (${games.count { it.owner.trim().equals(myName, ignoreCase = true) }})") },
                        )
                    }
                    FilterChip(
                        selected = showFilters || activeFilters > 0,
                        onClick = { showFilters = !showFilters },
                        label = { Text(if (activeFilters > 0) "Filters ($activeFilters)" else "Filters") },
                        leadingIcon = { Icon(Icons.Filled.FilterList, null) },
                    )
                    FilterChip(
                        selected = onlyWanted,
                        onClick = { onlyWanted = !onlyWanted },
                        label = { Text("Mijn ♥ (${myWants.size})") },
                    )
                    FilterChip(
                        selected = filter == 1,
                        onClick = { filter = if (filter == 1) 0 else 1 },
                        label = { Text("Gaat mee (${games.count { !it.wishlist }})") },
                    )
                    FilterChip(
                        selected = filter == 2,
                        onClick = { filter = if (filter == 2) 0 else 2 },
                        label = { Text("Wenslijst (${games.count { it.wishlist }})") },
                    )
                }
                if (showFilters) {
                    Card(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Aantal spelers", style = MaterialTheme.typography.labelLarge)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(0, 1, 2, 3, 4, 5, 6, 7).forEach { n ->
                                    FilterChip(
                                        selected = players == n,
                                        onClick = { players = n },
                                        label = { Text(when (n) { 0 -> "Alle"; 7 -> "7+"; else -> "$n" }) },
                                    )
                                }
                            }
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
                            SelectField(
                                label = "Categorie",
                                value = category,
                                options = listOf("" to "Alle categorieën") + categories.map { c ->
                                    c to "$c (${games.count { it.category.ifBlank { "Overig" } == c }})"
                                },
                                onSelect = { category = it },
                            )
                            SelectField(
                                label = "Wie neemt het mee",
                                value = owner,
                                options = listOf("" to "Iedereen") + owners.map { o ->
                                    o to "$o (${games.count { it.owner.trim().equals(o, ignoreCase = true) }})"
                                },
                                onSelect = { owner = it },
                            )
                            if (activeFilters > 0) {
                                TextButton(onClick = {
                                    players = 0; weights = emptySet(); category = ""; owner = ""
                                }) { Text("Filters wissen") }
                            }
                        }
                    }
                }
                Text(
                    "${visible.size} van ${games.size} spellen · tik op ♥ voor spellen die je wilt spelen — Match gebruikt dat.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            if (visible.isEmpty()) {
                item {
                    EmptyHint(
                        if (games.isEmpty()) "Nog geen spellen. Tik op + en voeg toe wat iedereen meeneemt."
                        else "Geen spellen gevonden."
                    )
                }
            }
            items(visible, key = { it.id }) { g ->
                val fanNames = (wantCount[g.id].orEmpty().map { it.name } +
                    g.interested.filter { it !in claimedNames }).distinct()
                Card(onClick = { editing = g }, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(g.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                when {
                                    g.wishlist -> "Wenslijst · nog niemand neemt het mee"
                                    g.owner.isNotBlank() -> "Neemt mee: ${g.owner}"
                                    else -> "Eigenaar onbekend"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (g.wishlist) MaterialTheme.colorScheme.tertiary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                if (g.infoChecked) listOfNotNull(
                                    "${g.minPlayers}–${g.maxPlayers} spelers", "${g.durationMin} min", g.weight.label,
                                    g.category.ifBlank { null },
                                ).joinToString(" · ")
                                else "⚠ spelers/duur nog invullen (tik om te bewerken)",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (g.infoChecked) MaterialTheme.colorScheme.onSurfaceVariant
                                else MaterialTheme.colorScheme.error,
                            )
                            if (fanNames.isNotEmpty()) {
                                Text(
                                    "♥ " + fanNames.joinToString(", "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                            }
                        }
                        if (g.link.isNotBlank()) {
                            IconButton(onClick = {
                                runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(g.link)))
                                }
                            }) { Icon(Icons.AutoMirrored.Filled.OpenInNew, "Op BoardGameGeek") }
                        }
                        val wanted = g.id in myWants
                        IconToggleButton(checked = wanted, onCheckedChange = { vm.setWant(g.id, it) }) {
                            Icon(
                                if (wanted) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                contentDescription = if (wanted) "Wil ik spelen" else "Toevoegen aan mijn wensen",
                                tint = if (wanted) MaterialTheme.colorScheme.secondary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { editing = Game() },
            icon = { Icon(Icons.Filled.Add, null) },
            text = { Text("Spel") },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
        )
    }

    editing?.let { g ->
        GameEditor(
            initial = g,
            onDismiss = { editing = null },
            onSave = { vm.saveGame(it); editing = null },
            onDelete = { vm.deleteGame(g.id); editing = null },
        )
    }
}

@Composable
private fun GameEditor(
    initial: Game,
    onDismiss: () -> Unit,
    onSave: (Game) -> Unit,
    onDelete: () -> Unit,
) {
    var name by remember { mutableStateOf(initial.name) }
    var minP by remember { mutableIntStateOf(initial.minPlayers) }
    var maxP by remember { mutableIntStateOf(initial.maxPlayers) }
    var duration by remember { mutableIntStateOf(initial.durationMin) }
    var weight by remember { mutableStateOf(initial.weight) }
    var owner by remember { mutableStateOf(initial.owner) }
    var notes by remember { mutableStateOf(initial.notes) }
    var link by remember { mutableStateOf(initial.link) }
    var wishlist by remember { mutableStateOf(initial.wishlist) }
    var category by remember { mutableStateOf(initial.category) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id.isBlank()) "Spel toevoegen" else "Spel bewerken") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Naam") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth(),
                )
                NumberField("Min. spelers", minP, {
                    minP = it; if (maxP < it) maxP = it
                }, 1..20)
                NumberField("Max. spelers", maxP, {
                    maxP = it; if (minP > it) minP = it
                }, 1..20)
                NumberField("Speelduur", duration, { duration = it }, 15..600, step = 15, display = { "$it min" })
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Weight.entries.forEach { w ->
                        FilterChip(selected = weight == w, onClick = { weight = w }, label = { Text(w.label) })
                    }
                }
                SelectField(
                    label = "Categorie",
                    value = category,
                    options = listOf("" to "Geen") + GAME_CATEGORIES.map { it to it },
                    onSelect = { category = it },
                )
                if (initial.categories.isNotEmpty()) {
                    Text(
                        "BGG: " + initial.categories.joinToString(", "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = wishlist, onCheckedChange = { wishlist = it })
                    Text("Wenslijst (niemand neemt het mee)")
                }
                OutlinedTextField(
                    value = owner, onValueChange = { owner = it; if (it.isNotBlank()) wishlist = false },
                    label = { Text("Wie neemt het mee") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = notes, onValueChange = { notes = it },
                    label = { Text("Notitie") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = link, onValueChange = { link = it },
                    label = { Text("Link (BoardGameGeek)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (!initial.infoChecked) {
                    Text(
                        "Spelers en duur zijn nog standaardwaarden. Controleer ze via de link en sla op.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (initial.id.isNotBlank()) {
                    TextButton(onClick = onDelete) {
                        Text("Spel verwijderen", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    onSave(
                        initial.copy(
                            name = name.trim(),
                            minPlayers = minP,
                            maxPlayers = maxP,
                            durationMin = duration,
                            weight = weight,
                            owner = owner.trim(),
                            notes = notes.trim(),
                            link = link.trim(),
                            wishlist = wishlist && owner.isBlank(),
                            infoChecked = true,
                            category = category,
                        )
                    )
                },
            ) { Text("Opslaan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuleren") } },
    )
}
