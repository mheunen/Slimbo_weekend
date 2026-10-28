@file:OptIn(ExperimentalLayoutApi::class)

package nl.spellenweekend.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bed
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.spellenweekend.app.data.Chore
import nl.spellenweekend.app.data.Guest
import nl.spellenweekend.app.data.Member
import nl.spellenweekend.app.data.Room
import nl.spellenweekend.app.data.WeekendEvent

private val DAY_ORDER = listOf("Vrijdag", "Zaterdag", "Zondag", "Maandag")

fun choreLabel(c: Chore): String {
    val slot = when (c.slot) {
        "ochtend" -> "ochtend (tot 13:00)"
        "middag" -> "middag (13–18:00)"
        "avond" -> "avond (vanaf 18:00)"
        else -> c.slot
    }
    return "${c.day.lowercase()} $slot"
}

fun floorLabel(floor: Int): String = when (floor) {
    0 -> "Begane grond"
    1 -> "1e verdieping"
    2 -> "2e verdieping"
    else -> "${floor}e verdieping"
}

// =====================================================================
// Nu-scherm: kaartjes voor importeren en "mijn verblijf"
// =====================================================================

@Composable
fun ImportCard(vm: AppViewModel) {
    val importing by vm.importing.collectAsStateWithLifecycle()
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Gegevens Slimbo-weekend laden", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Molen van Medael (residentie 17 en 18), kamerindeling, corveerooster, " +
                    "spellenlijst met interesse, afspraken en wat je mee moet nemen.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            Button(onClick = { vm.importSeed() }, enabled = !importing) {
                if (importing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Icon(Icons.Filled.CloudDownload, null)
                Spacer(Modifier.width(8.dp))
                Text("Laden")
            }
        }
    }
}

@Composable
fun MyStayCard(myself: Member?, look: Lookup, vm: AppViewModel) {
    var changing by remember { mutableStateOf(false) }
    val linked = !myself?.guestId.isNullOrBlank()

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            if (!linked || changing) {
                GuestClaim(myself, look.guests, onClaim = { g ->
                    vm.claimGuest(g, myself?.guestId ?: "")
                    changing = false
                })
                if (changing) TextButton(onClick = { changing = false }) { Text("Annuleren") }
                return@Column
            }
            val room = look.roomOf(myself)
            val me = look.guestName(myself)
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Icons.Filled.Bed)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    if (room != null) {
                        Text(
                            "${look.placeName(room.placeId)} · ${room.name}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        val others = room.occupants.filter { it != me }
                        Text(
                            floorLabel(room.floor) + if (others.isNotEmpty()) " · met ${others.joinToString(", ")}" else "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text("Geen kamer gevonden voor $me", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            val mine = look.choresOf(myself)
            if (mine.isNotEmpty()) {
                Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(top = 12.dp)) {
                    IconBadge(Icons.Filled.CleaningServices)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Jouw corvee", style = MaterialTheme.typography.titleSmall)
                        mine.forEach { c ->
                            val with = c.names.filter { it != me }
                            Text(
                                choreLabel(c).replaceFirstChar { it.uppercase() } +
                                    if (with.isNotEmpty()) " · met ${with.joinToString(", ")}" else "",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
            TextButton(onClick = { changing = true }) { Text("Ben je iemand anders? Wijzig") }
        }
    }
}

@Composable
private fun GuestClaim(myself: Member?, guests: List<Guest>, onClaim: (Guest) -> Unit) {
    val free = guests.filter { it.claimedBy.isBlank() || it.id == myself?.guestId }
        .sortedBy { it.name.lowercase() }
    val guess = free.firstOrNull { it.name.equals(myself?.name?.trim(), ignoreCase = true) }
    var selected by remember(guess) { mutableStateOf(guess?.id ?: "") }

    Text("Wie ben jij in de kamerindeling?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    Text(
        "Dan zie je je kamer en corvee, en komen de spellen waar je in het document interesse in had op je ♥-lijst.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 8.dp),
    )
    SelectField(
        label = "Naam",
        value = selected,
        options = listOf("" to "Kies je naam…") + free.map { it.id to it.name },
        onSelect = { selected = it },
    )
    Button(
        enabled = selected.isNotBlank(),
        onClick = { free.firstOrNull { it.id == selected }?.let(onClaim) },
        modifier = Modifier.padding(top = 8.dp),
    ) { Text("Koppelen") }
}

// =====================================================================
// Meer → Weekend
// =====================================================================

@Composable
fun WeekendInfoTab(event: WeekendEvent?, vm: AppViewModel) {
    val context = LocalContext.current
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { SlimboLogo(Modifier.fillMaxWidth()) }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(event?.name ?: "", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    if (!event?.venue.isNullOrBlank()) {
                        Text(event!!.venue, style = MaterialTheme.typography.titleMedium)
                        Text(event.address, style = MaterialTheme.typography.bodyMedium)
                    }
                    if ((event?.checkInAt ?: 0L) > 0L) {
                        Text(
                            "Inchecken ${dayLabel(toDate(event!!.checkInAt)).lowercase()} vanaf ${fmtTime(event.checkInAt)} · " +
                                "uitchecken ${dayLabel(toDate(event.checkOutAt)).lowercase()} tot ${fmtTime(event.checkOutAt)}",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 10.dp),
                    ) {
                        if (!event?.address.isNullOrBlank()) {
                            FilledTonalButton(onClick = {
                                val uri = Uri.parse("geo:0,0?q=" + Uri.encode(event!!.address))
                                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                            }) { Icon(Icons.Filled.Map, null); Spacer(Modifier.width(6.dp)); Text("Route") }
                        }
                        OutlinedButton(onClick = { openUrl(context, SLIMBO_URL) }) {
                            Icon(Icons.Filled.Language, null); Spacer(Modifier.width(6.dp)); Text("slimbo.nl")
                        }
                        if (!event?.phone.isNullOrBlank()) {
                            OutlinedButton(onClick = {
                                val uri = Uri.parse("tel:" + event!!.phone.replace(" ", ""))
                                runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, uri)) }
                            }) { Icon(Icons.Filled.Phone, null); Spacer(Modifier.width(6.dp)); Text("Bellen") }
                        }
                    }
                }
            }
        }
        item { JoinCodeCard(event) }
        if (event != null && event.info.isEmpty()) {
            item { Spacer(Modifier.padding(4.dp)) }
            item { ImportCard(vm) }
        }
        items(event?.info.orEmpty(), key = { "i-" + it.title }) { section ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(section.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    section.items.forEach { line ->
                        Row(Modifier.padding(top = 4.dp)) {
                            Text("•", modifier = Modifier.width(14.dp))
                            Text(line, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.padding(bottom = 24.dp)) }
    }
}

@Composable
fun JoinCodeCard(event: WeekendEvent?) {
    val context = LocalContext.current
    Card(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp)) {
            Column(Modifier.weight(1f)) {
                Text("Code om mee te doen", style = MaterialTheme.typography.labelMedium)
                Text(
                    event?.joinCode ?: "",
                    style = MaterialTheme.typography.headlineMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                )
            }
            FilledTonalButton(onClick = {
                val text = "Doe mee met \"${event?.name}\" in de Spellenweekend-app. Code: ${event?.joinCode}"
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                }
                context.startActivity(Intent.createChooser(send, "Code delen"))
            }) {
                Icon(Icons.Filled.Share, null); Spacer(Modifier.width(6.dp)); Text("Delen")
            }
        }
    }
}

// =====================================================================
// Meer → Kamers
// =====================================================================

@Composable
fun RoomsTab(look: Lookup, myself: Member?, rooms: List<Room>, guests: List<Guest>, vm: AppViewModel) {
    val myRoom = look.roomOf(myself)
    val claimed = guests.filter { it.claimedBy.isNotBlank() }.map { it.name }.toSet()
    val byPlace = rooms.groupBy { it.placeId }.toSortedMap()

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (rooms.isEmpty()) {
            item { EmptyHint("Nog geen kamerindeling.") }
            item { ImportCard(vm) }
        }
        byPlace.forEach { (placeId, list) ->
            val beds = list.sumOf { it.beds }
            val people = list.sumOf { it.occupants.size }
            item(key = "h-$placeId") { SectionHeader("${look.placeName(placeId).ifBlank { "Overig" }} · $people personen, $beds bedden") }
            list.groupBy { it.floor }.toSortedMap().forEach { (floor, roomsOnFloor) ->
                item(key = "f-$placeId-$floor") {
                    Text(
                        floorLabel(floor),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(roomsOnFloor.sortedBy { it.name }, key = { it.id }) { r ->
                    val isMine = r.id == myRoom?.id
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isMine) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceContainer,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(r.name + if (isMine) " · jouw kamer" else "", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                Text("${r.occupants.size}/${r.beds}", style = MaterialTheme.typography.labelLarge)
                            }
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(vertical = 6.dp),
                            ) {
                                r.occupants.forEach { n -> NameTag(n, active = n in claimed) }
                                repeat((r.beds - r.occupants.size).coerceAtLeast(0)) { NameTag("vrij bed", active = false, muted = true) }
                            }
                            Text(r.bedInfo, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        if (rooms.isNotEmpty()) {
            item {
                Text(
                    "Groen = gebruikt de app al. Kamers ruilen mag; geef het door aan de organisatie.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
                )
            }
        }
    }
}

@Composable
private fun NameTag(text: String, active: Boolean, muted: Boolean = false) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = when {
            muted -> MaterialTheme.colorScheme.surface
            active -> statusColor(nl.spellenweekend.app.data.MemberStatus.BESCHIKBAAR).copy(alpha = 0.25f)
            else -> MaterialTheme.colorScheme.surfaceContainerHigh
        },
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

// =====================================================================
// Meer → Corvee
// =====================================================================

@Composable
fun ChoresTab(look: Lookup, myself: Member?, chores: List<Chore>, vm: AppViewModel) {
    val me = look.guestName(myself)
    val byPlace = chores.groupBy { it.placeId }.toSortedMap()
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text(
                "Iedereen ruimt zijn eigen spullen op. Corvee is een extra taak voor de gezamenlijke ruimtes: " +
                    "vaatwasser aanzetten/uitruimen, wc-papier controleren, vuilniszakken verversen.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (chores.isEmpty()) {
            item { EmptyHint("Nog geen corveerooster.") }
            item { ImportCard(vm) }
        }
        byPlace.forEach { (placeId, list) ->
            item(key = "ch-$placeId") { SectionHeader(look.placeName(placeId).ifBlank { "Corvee" }) }
            val byDay = list.groupBy { it.day }.toList().sortedBy { DAY_ORDER.indexOf(it.first) }
            byDay.forEach { (day, dayChores) ->
                item(key = "cd-$placeId-$day") {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp)) {
                            Text(day, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            dayChores.sortedBy { it.startAt }.forEach { c ->
                                val mine = me != null && me in c.names
                                Row(Modifier.padding(top = 4.dp)) {
                                    Text(
                                        c.slot.replaceFirstChar { it.uppercase() },
                                        style = MaterialTheme.typography.labelLarge,
                                        modifier = Modifier.width(76.dp),
                                    )
                                    Text(
                                        c.names.joinToString(", "),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (mine) FontWeight.Bold else FontWeight.Normal,
                                        color = if (mine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    )
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
