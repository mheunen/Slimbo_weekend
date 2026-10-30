package nl.spellenweekend.app.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TableRestaurant
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import nl.spellenweekend.app.data.Activity
import nl.spellenweekend.app.data.Chore
import nl.spellenweekend.app.data.Guest
import nl.spellenweekend.app.data.Room
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.spellenweekend.app.data.ActivityStatus
import nl.spellenweekend.app.data.GameTable
import nl.spellenweekend.app.data.Member
import nl.spellenweekend.app.data.Place
import nl.spellenweekend.app.data.WeekendEvent

@Composable
fun MoreScreen(
    vm: AppViewModel,
    me: String,
    event: WeekendEvent?,
    look: Lookup,
    members: List<Member>,
    places: List<Place>,
    tables: List<GameTable>,
    activities: List<Activity>,
    rooms: List<Room>,
    guests: List<Guest>,
    chores: List<Chore>,
) {
    var sub by rememberSaveable { mutableIntStateOf(0) }
    val myself = look.members[me]
    Column {
        val context = androidx.compose.ui.platform.LocalContext.current
        Row(verticalAlignment = Alignment.CenterVertically) {
            ScrollableTabRow(selectedTabIndex = sub, edgePadding = 8.dp, modifier = Modifier.weight(1f)) {
                listOf("Weekend", "Kamers", "Corvee", "Beheer").forEachIndexed { i, label ->
                    Tab(selected = sub == i, onClick = { sub = i }, text = { Text(label) })
                }
            }
            // Altijd zichtbaar: snel naar de Slimbo-website
            IconButton(onClick = { openUrl(context, SLIMBO_URL) }) {
                Icon(androidx.compose.material.icons.Icons.Filled.Language, contentDescription = "slimbo.nl")
            }
        }
        when (sub) {
            0 -> WeekendInfoTab(event, vm)
            1 -> RoomsTab(look, myself, rooms, guests, vm)
            2 -> ChoresTab(look, myself, chores, vm)
            else -> ManageTab(vm, me, look, members, places, tables, activities)
        }
    }
}

@Composable
private fun ManageTab(
    vm: AppViewModel,
    me: String,
    look: Lookup,
    members: List<Member>,
    places: List<Place>,
    tables: List<GameTable>,
    activities: List<Activity>,
) {
    var editPlace by remember { mutableStateOf<Place?>(null) }
    var editTable by remember { mutableStateOf<GameTable?>(null) }
    var confirmLeave by remember { mutableStateOf(false) }
    val importing by vm.importing.collectAsStateWithLifecycle()
    val now = System.currentTimeMillis()

    val busyTables = activities
        .filter { it.status == ActivityStatus.GESTART && it.tableId.isNotBlank() }
        .associateBy { it.tableId }
    val nextOnTable = activities
        .filter { it.status == ActivityStatus.GEPLAND && it.tableId.isNotBlank() && it.endAt >= now }
        .sortedBy { it.startAt }
        .groupBy { it.tableId }
        .mapValues { it.value.first() }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // ---------- mijn naam ----------
        item { SectionHeader("Jij") }
        item { RenameField(look.members[me]?.name ?: "", vm::rename) }

        // ---------- locaties ----------
        item { HeaderWithAdd("Locaties (${places.size})") { editPlace = Place() } }
        if (places.isEmpty()) item { EmptyHint("Bijvoorbeeld: Woonkamer, Serre, Zolder, Tuin.") }
        items(places, key = { "p-" + it.id }) { p ->
            val n = tables.count { it.placeId == p.id }
            ListItem(
                headlineContent = { Text(p.name) },
                supportingContent = {
                    Text(listOfNotNull(p.description.ifBlank { null }, "$n tafel${if (n == 1) "" else "s"}").joinToString(" · "))
                },
                leadingContent = { Icon(Icons.Filled.Place, null) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable { editPlace = p },
            )
        }

        // ---------- tafels ----------
        item { HeaderWithAdd("Tafels (${tables.size})") { editTable = GameTable(placeId = places.firstOrNull()?.id ?: "") } }
        if (tables.isEmpty()) item { EmptyHint("Voeg tafels toe met het aantal plaatsen; Match zoekt er een passende bij.") }
        items(tables, key = { "t-" + it.id }) { t ->
            val running = busyTables[t.id]
            val next = nextOnTable[t.id]
            val state = when {
                running != null -> "Bezet: ${running.title}"
                next != null -> "Vrij · om ${fmtTime(next.startAt)} ${next.title}"
                else -> "Vrij"
            }
            ListItem(
                headlineContent = { Text(t.name) },
                supportingContent = {
                    Text(listOfNotNull("${t.seats} plaatsen", look.places[t.placeId]?.name, state).joinToString(" · "))
                },
                leadingContent = {
                    Icon(
                        Icons.Filled.TableRestaurant, null,
                        tint = if (running != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    )
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable { editTable = t },
            )
        }

        // ---------- deelnemers ----------
        item { SectionHeader("Deelnemers (${members.size})") }
        items(members.sortedBy { it.name.lowercase() }, key = { "m-" + it.id }) { m ->
            ListItem(
                headlineContent = { Text(m.name + if (m.id == me) " (jij)" else "") },
                supportingContent = {
                    Text(listOfNotNull(m.status.label, m.statusNote.ifBlank { null }).joinToString(" · "))
                },
                leadingContent = { StatusDot(m.status, 14.dp) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
        }

        item { SectionHeader("App") }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Versie ${vm.currentVersionName}", modifier = Modifier.weight(1f))
                OutlinedButton(onClick = { vm.checkForUpdate(manual = true) }) { Text("Controleren op updates") }
            }
        }
        item { SectionHeader("Gegevens") }
        item {
            OutlinedButton(onClick = { vm.importSeed() }, enabled = !importing) {
                Text(if (importing) "Bezig met laden…" else "Slimbo-gegevens (opnieuw) laden")
            }
        }
        item {
            Text(
                "Kamers, gastenlijst, corvee en weekendinfo worden bijgewerkt. " +
                    "Spellen, locaties en tafels die al bestaan blijven zoals ze zijn.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            OutlinedButton(
                onClick = { confirmLeave = true },
                modifier = Modifier.padding(top = 24.dp, bottom = 32.dp),
            ) { Text("Ander weekend kiezen") }
        }
    }

    editPlace?.let { p ->
        PlaceEditor(
            initial = p,
            onDismiss = { editPlace = null },
            onSave = { vm.savePlace(it); editPlace = null },
            onDelete = { vm.deletePlace(p.id); editPlace = null },
        )
    }
    editTable?.let { t ->
        TableEditor(
            initial = t,
            places = places,
            onDismiss = { editTable = null },
            onSave = { vm.saveTable(it); editTable = null },
            onDelete = { vm.deleteTable(t.id); editTable = null },
        )
    }
    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text("Ander weekend kiezen?") },
            text = { Text("Je kunt later met dezelfde code terugkomen in dit weekend.") },
            confirmButton = { TextButton(onClick = { confirmLeave = false; vm.leaveEvent() }) { Text("Doorgaan") } },
            dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text("Annuleren") } },
        )
    }
}

@Composable
private fun HeaderWithAdd(text: String, onAdd: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        SectionHeader(text, Modifier.weight(1f))
        IconButton(onClick = onAdd, modifier = Modifier.padding(top = 12.dp)) { Icon(Icons.Filled.Add, "Toevoegen") }
    }
}

@Composable
private fun RenameField(current: String, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf(current) }
    LaunchedEffect(current) { name = current }
    OutlinedTextField(
        value = name,
        onValueChange = { name = it },
        label = { Text("Jouw naam") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
        trailingIcon = {
            if (name.isNotBlank() && name != current) {
                IconButton(onClick = { onSave(name) }) { Icon(Icons.Filled.Check, "Opslaan") }
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun PlaceEditor(initial: Place, onDismiss: () -> Unit, onSave: (Place) -> Unit, onDelete: () -> Unit) {
    var name by remember { mutableStateOf(initial.name) }
    var description by remember { mutableStateOf(initial.description) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id.isBlank()) "Locatie toevoegen" else "Locatie bewerken") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it }, label = { Text("Naam") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = description, onValueChange = { description = it }, label = { Text("Omschrijving") },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (initial.id.isNotBlank()) {
                    TextButton(onClick = onDelete) { Text("Locatie verwijderen", color = MaterialTheme.colorScheme.error) }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = {
                onSave(initial.copy(name = name.trim(), description = description.trim()))
            }) { Text("Opslaan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuleren") } },
    )
}

@Composable
private fun TableEditor(
    initial: GameTable,
    places: List<Place>,
    onDismiss: () -> Unit,
    onSave: (GameTable) -> Unit,
    onDelete: () -> Unit,
) {
    var name by remember { mutableStateOf(initial.name) }
    var placeId by remember { mutableStateOf(initial.placeId) }
    var seats by remember { mutableIntStateOf(initial.seats) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id.isBlank()) "Tafel toevoegen" else "Tafel bewerken") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it }, label = { Text("Naam") },
                    placeholder = { Text("bv. Grote tafel") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
                SelectField(
                    label = "Locatie",
                    value = placeId,
                    options = listOf("" to "Geen") + places.map { it.id to it.name },
                    onSelect = { placeId = it },
                )
                NumberField("Plaatsen", seats, { seats = it }, 1..20)
                if (initial.id.isNotBlank()) {
                    TextButton(onClick = onDelete) { Text("Tafel verwijderen", color = MaterialTheme.colorScheme.error) }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = {
                onSave(initial.copy(name = name.trim(), placeId = placeId, seats = seats))
            }) { Text("Opslaan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuleren") } },
    )
}
