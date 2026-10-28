@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package nl.spellenweekend.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import nl.spellenweekend.app.data.Activity
import nl.spellenweekend.app.data.ActivityStatus
import nl.spellenweekend.app.data.ActivityType
import nl.spellenweekend.app.data.Member
import java.time.LocalDate
import java.time.ZoneId

// =====================================================================
// Kaartje voor één activiteit
// =====================================================================

@Composable
fun ActivityCard(
    a: Activity,
    me: String,
    look: Lookup,
    vm: AppViewModel,
    onEdit: (Activity) -> Unit,
    modifier: Modifier = Modifier,
) {
    val iAmIn = me in a.participants
    val iAmInvited = me in a.invited
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val container = when {
        a.status == ActivityStatus.GESTART -> MaterialTheme.colorScheme.primaryContainer
        iAmInvited && a.isOpen -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainer
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = container),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                IconBadge(typeIcon(a.type))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        a.title.ifBlank { a.type.label },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val where = look.whereText(a)
                    Text(
                        buildString {
                            append(dayLabel(toDate(a.startAt)))
                            append(" ")
                            append(fmtRange(a.startAt, a.endAt))
                            if (where.isNotBlank()) append(" · ").append(where)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (a.status != ActivityStatus.GEPLAND) {
                        Text(
                            a.status.label,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (a.status == ActivityStatus.GESTART)
                                MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Meer") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Bewerken") }, onClick = {
                            menu = false; onEdit(a)
                        })
                        if (a.isOpen) {
                            DropdownMenuItem(text = { Text("Annuleren") }, onClick = {
                                menu = false; vm.setActivityStatus(a, ActivityStatus.GEANNULEERD)
                            })
                        } else {
                            DropdownMenuItem(text = { Text("Opnieuw plannen") }, onClick = {
                                menu = false; vm.setActivityStatus(a, ActivityStatus.GEPLAND)
                            })
                        }
                        DropdownMenuItem(text = { Text("Verwijderen") }, onClick = {
                            menu = false; confirmDelete = true
                        })
                    }
                }
            }

            Column(Modifier.padding(top = 8.dp, end = 12.dp)) {
                val count = if (a.maxParticipants > 0)
                    "${a.participants.size}/${a.maxParticipants}" else "${a.participants.size}"
                Text(
                    "Doen mee ($count): " + (look.names(a.participants).ifBlank { "nog niemand" }),
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (a.invited.isNotEmpty()) {
                    Text(
                        "Uitgenodigd: " + look.names(a.invited),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (a.declined.isNotEmpty()) {
                    Text(
                        "Afgemeld: " + look.names(a.declined),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (a.notes.isNotBlank()) {
                    Text(
                        a.notes,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }

            if (a.isOpen) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 8.dp, end = 12.dp),
                ) {
                    when {
                        iAmInvited -> {
                            Button(onClick = { vm.respond(a, true) }) {
                                Icon(Icons.Filled.Check, null); Spacer(Modifier.width(6.dp)); Text("Ik doe mee")
                            }
                            OutlinedButton(onClick = { vm.respond(a, false) }) { Text("Nee, dank je") }
                        }
                        !iAmIn && !a.isFull -> {
                            FilledTonalButton(onClick = { vm.join(a) }) { Text("Doe mee") }
                        }
                        iAmIn -> {
                            if (a.status == ActivityStatus.GEPLAND) {
                                Button(onClick = { vm.setActivityStatus(a, ActivityStatus.GESTART) }) {
                                    Icon(Icons.Filled.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text("Start")
                                }
                            } else {
                                Button(onClick = { vm.setActivityStatus(a, ActivityStatus.AFGEROND) }) {
                                    Icon(Icons.Filled.Done, null); Spacer(Modifier.width(6.dp)); Text("Klaar")
                                }
                            }
                            TextButton(onClick = { vm.leave(a) }) { Text("Afmelden") }
                        }
                        else -> {
                            AssistChip(onClick = {}, label = { Text("Vol") })
                        }
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Verwijderen?") },
            text = { Text("\"${a.title}\" wordt voor iedereen verwijderd.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; vm.deleteActivity(a.id) }) { Text("Verwijderen") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Annuleren") } },
        )
    }
}

// =====================================================================
// Editor voor een nieuwe of bestaande activiteit (schermvullend)
// =====================================================================

@Composable
fun ActivityEditor(
    initial: Activity,
    me: String,
    look: Lookup,
    members: List<Member>,
    onDismiss: () -> Unit,
    onSave: (Activity) -> Unit,
) {
    var type by remember { mutableStateOf(initial.type) }
    var gameId by remember { mutableStateOf(initial.gameId) }
    var title by remember { mutableStateOf(initial.title) }
    var startAt by remember { mutableLongStateOf(if (initial.startAt == 0L) roundedNow() else initial.startAt) }
    var duration by remember { mutableIntStateOf(initial.durationMin) }
    var placeId by remember { mutableStateOf(initial.placeId) }
    var tableId by remember { mutableStateOf(initial.tableId) }
    var maxP by remember { mutableIntStateOf(initial.maxParticipants) }
    var invited by remember { mutableStateOf(initial.invited.toSet()) }
    var notes by remember { mutableStateOf(initial.notes) }
    var showTime by remember { mutableStateOf(false) }

    val zone = ZoneId.systemDefault()
    val curDate = toDate(startAt)
    val today = LocalDate.now(zone)
    val days = (0L..3L).map { today.plusDays(it) }.let { if (curDate in it) it else listOf(curDate) + it }

    fun defaultTitle(t: ActivityType) = when (t) {
        ActivityType.ETEN -> "Eten"
        ActivityType.WANDELEN -> "Wandeling"
        else -> ""
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(Modifier.fillMaxSize()) {
            Column {
                TopAppBar(
                    title = { Text(if (initial.id.isBlank()) "Nieuwe activiteit" else "Bewerken") },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, "Sluiten") }
                    },
                    actions = {
                        TextButton(
                            enabled = title.isNotBlank(),
                            onClick = {
                                onSave(
                                    initial.copy(
                                        type = type,
                                        gameId = if (type == ActivityType.SPEL) gameId else "",
                                        title = title.trim(),
                                        startAt = startAt,
                                        durationMin = duration,
                                        placeId = placeId,
                                        tableId = tableId,
                                        maxParticipants = maxP,
                                        invited = (invited - initial.participants.toSet()).toList(),
                                        declined = initial.declined.filter { it !in invited },
                                        notes = notes.trim(),
                                        reminderSent = if (startAt != initial.startAt) false else initial.reminderSent,
                                    )
                                )
                            },
                        ) { Text("Opslaan") }
                    },
                )
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .imePadding()
                        .padding(16.dp),
                ) {
                    // --- soort ---
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ActivityType.entries.forEach { t ->
                            FilterChip(
                                selected = type == t,
                                onClick = {
                                    if (title.isBlank() || title == defaultTitle(type)) title = defaultTitle(t)
                                    type = t
                                },
                                label = { Text(t.label) },
                                leadingIcon = { Icon(typeIcon(t), null) },
                            )
                        }
                    }

                    // --- spel ---
                    if (type == ActivityType.SPEL) {
                        val games = look.games.values.sortedBy { it.name.lowercase() }
                        if (games.isEmpty()) {
                            Text(
                                "Nog geen spellen in de lijst. Voeg ze toe op het tabblad Spellen, of typ hieronder een titel.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            SelectField(
                                label = "Spel",
                                value = gameId,
                                options = listOf("" to "Kies een spel…") +
                                    games.map { it.id to "${it.name} (${it.minPlayers}–${it.maxPlayers})" },
                                onSelect = { id ->
                                    gameId = id
                                    look.games[id]?.let { g ->
                                        title = g.name
                                        maxP = g.maxPlayers
                                        duration = g.durationMin
                                    }
                                },
                            )
                        }
                    }

                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Titel") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    // --- wanneer ---
                    Text("Wanneer", style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        days.forEach { d ->
                            FilterChip(
                                selected = d == curDate,
                                onClick = { startAt = epochOf(d, hourOf(startAt), minuteOf(startAt)) },
                                label = { Text(dayLabel(d)) },
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = { showTime = true }) {
                            Icon(Icons.Filled.Schedule, null)
                            Spacer(Modifier.width(8.dp))
                            Text(fmtTime(startAt), style = MaterialTheme.typography.titleMedium)
                        }
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = { startAt = roundedNow() }) { Text("Zo snel mogelijk") }
                    }
                    NumberField(
                        label = "Duur",
                        value = duration,
                        onChange = { duration = it },
                        range = 15..600,
                        step = 15,
                        display = { m -> if (m % 60 == 0) "${m / 60} u" else "${m / 60}u${"%02d".format(m % 60)}" },
                    )

                    // --- waar ---
                    Text("Waar", style = MaterialTheme.typography.titleSmall)
                    val places = look.places.values.sortedBy { it.name.lowercase() }
                    SelectField(
                        label = "Locatie",
                        value = placeId,
                        options = listOf("" to "Geen / n.v.t.") + places.map { it.id to it.name },
                        onSelect = { id ->
                            placeId = id
                            if (look.tables[tableId]?.placeId != id) tableId = ""
                        },
                    )
                    val tables = look.tables.values
                        .filter { placeId.isBlank() || it.placeId == placeId }
                        .sortedBy { it.name.lowercase() }
                    if (tables.isNotEmpty()) {
                        SelectField(
                            label = "Tafel",
                            value = tableId,
                            options = listOf("" to "Geen tafel") + tables.map { t ->
                                val p = look.places[t.placeId]?.name
                                t.id to (t.name + " · ${t.seats} pl." + (p?.let { " · $it" } ?: ""))
                            },
                            onSelect = { id ->
                                tableId = id
                                look.tables[id]?.placeId?.takeIf { it.isNotBlank() }?.let { placeId = it }
                            },
                        )
                    }

                    // --- wie ---
                    NumberField(
                        label = "Max. deelnemers",
                        value = maxP,
                        onChange = { maxP = it },
                        range = 0..40,
                        display = { if (it == 0) "geen" else it.toString() },
                    )
                    Text("Uitnodigen", style = MaterialTheme.typography.titleSmall)
                    val others = members.filter { it.id != me }.sortedBy { it.name.lowercase() }
                    if (others.isEmpty()) {
                        Text(
                            "Nog niemand anders in dit weekend. Deel de code via het tabblad Meer.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    others.forEach { m ->
                        val joined = m.id in initial.participants
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !joined) {
                                    invited = if (m.id in invited) invited - m.id else invited + m.id
                                },
                        ) {
                            Checkbox(
                                checked = joined || m.id in invited,
                                onCheckedChange = null,
                                enabled = !joined,
                            )
                            StatusDot(m.status)
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(m.name)
                                val sub = when {
                                    joined -> "doet al mee"
                                    m.id in initial.declined -> "had afgemeld"
                                    m.statusNote.isNotBlank() -> "${m.status.label} · ${m.statusNote}"
                                    else -> m.status.label
                                }
                                Text(
                                    sub,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    HorizontalDivider()
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Notities (optioneel)") },
                        minLines = 2,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.padding(bottom = 24.dp))
                }
            }
        }
    }

    if (showTime) {
        val state = rememberTimePickerState(
            initialHour = hourOf(startAt),
            initialMinute = minuteOf(startAt),
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { showTime = false },
            confirmButton = {
                TextButton(onClick = {
                    startAt = epochOf(curDate, state.hour, state.minute)
                    showTime = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showTime = false }) { Text("Annuleren") } },
            text = { TimePicker(state = state) },
        )
    }
}
