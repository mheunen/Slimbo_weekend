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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import nl.spellenweekend.app.data.Activity
import nl.spellenweekend.app.data.ActivityStatus
import nl.spellenweekend.app.data.ActivityType
import nl.spellenweekend.app.data.Member
import nl.spellenweekend.app.data.MemberStatus

@Composable
fun NowScreen(
    vm: AppViewModel,
    me: String,
    look: Lookup,
    members: List<Member>,
    activities: List<Activity>,
    onEdit: (Activity) -> Unit,
    onOpenTab: (MainTab) -> Unit,
) {
    val myself = look.members[me]
    val now = System.currentTimeMillis()

    val invites = activities.filter { me in it.invited && it.isOpen }
    val running = activities.filter { it.status == ActivityStatus.GESTART }
    val upcoming = activities
        .filter { it.status == ActivityStatus.GEPLAND && it.endAt >= now && me !in it.invited }
        .sortedBy { it.startAt }
        .take(6)

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { MyStatusCard(myself, vm) }
        if (look.event != null && look.event.seedVersion == 0 && look.guests.isEmpty()) {
            item { ImportCard(vm) }
        }
        if (look.guests.isNotEmpty()) {
            item { MyStayCard(myself, look, vm) }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                FilledTonalButton(onClick = {
                    onEdit(Activity(type = ActivityType.SPEL, startAt = roundedNow()))
                }) {
                    Icon(Icons.Filled.Add, null); Spacer(Modifier.width(6.dp)); Text("Plan iets")
                }
                OutlinedButton(onClick = { onOpenTab(MainTab.MATCH) }) {
                    Icon(Icons.Filled.AutoAwesome, null); Spacer(Modifier.width(6.dp)); Text("Wat kan ik spelen?")
                }
            }
        }

        if (invites.isNotEmpty()) {
            item { SectionHeader("Uitnodigingen voor jou") }
            items(invites, key = { "inv-" + it.id }) { ActivityCard(it, me, look, vm, onEdit) }
        }

        item { SectionHeader("Nu bezig") }
        if (running.isEmpty()) item { EmptyHint("Er loopt nu niets.") }
        items(running, key = { "run-" + it.id }) { ActivityCard(it, me, look, vm, onEdit) }

        item { SectionHeader("Straks") }
        if (upcoming.isEmpty()) item { EmptyHint("Nog niets gepland. Tik op \"Plan iets\".") }
        items(upcoming, key = { "up-" + it.id }) { ActivityCard(it, me, look, vm, onEdit) }

        item { SectionHeader("Wie is er (${members.size})") }
        item {
            val order = listOf(MemberStatus.BESCHIKBAAR, MemberStatus.RESERVEERBAAR, MemberStatus.BEZET)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                order.forEach { s ->
                    val group = members.filter { it.status == s }.sortedBy { it.name.lowercase() }
                    if (group.isNotEmpty()) {
                        Text(
                            "${s.label} (${group.size})",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            group.forEach { m ->
                                Column {
                                    MemberPill(m)
                                    if (m.statusNote.isNotBlank()) {
                                        Text(
                                            m.statusNote,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(start = 10.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.padding(bottom = 24.dp)) }
    }
}

@Composable
private fun MyStatusCard(myself: Member?, vm: AppViewModel) {
    val status = myself?.status ?: MemberStatus.BESCHIKBAAR
    var note by remember { mutableStateOf(myself?.statusNote ?: "") }
    LaunchedEffect(myself?.statusNote) { note = myself?.statusNote ?: "" }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "Hoi ${myself?.name ?: ""}, hoe sta je ervoor?",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 8.dp),
            ) {
                MemberStatus.entries.forEach { s ->
                    FilterChip(
                        selected = status == s,
                        onClick = { vm.setStatus(s, note) },
                        label = { Text(s.label) },
                        leadingIcon = { StatusDot(s) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = statusColor(s).copy(alpha = 0.22f),
                        ),
                    )
                }
            }
            Text(
                when (status) {
                    MemberStatus.BESCHIKBAAR -> "Je kunt direct aanschuiven."
                    MemberStatus.RESERVEERBAAR -> "Je bent vrij, maar wilt eerst gevraagd worden."
                    MemberStatus.BEZET -> "Je zit in een spel of bent even weg."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = note,
                onValueChange = { note = it.take(60) },
                label = { Text("Toelichting (optioneel)") },
                placeholder = { Text("bv. tot 15:00 vrij, even boodschappen") },
                singleLine = true,
                trailingIcon = {
                    if (note != (myself?.statusNote ?: "")) {
                        IconButton(onClick = { vm.setStatus(status, note) }) {
                            Icon(Icons.Filled.Check, "Opslaan")
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            )
        }
    }
}
