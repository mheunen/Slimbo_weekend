@file:OptIn(ExperimentalMaterial3Api::class)

package nl.spellenweekend.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import nl.spellenweekend.app.data.Activity
import nl.spellenweekend.app.data.Chore
import nl.spellenweekend.app.data.Guest
import nl.spellenweekend.app.data.Room
import nl.spellenweekend.app.data.WeekendEvent
import nl.spellenweekend.app.data.ActivityType
import nl.spellenweekend.app.data.Game
import nl.spellenweekend.app.data.GameTable
import nl.spellenweekend.app.data.Member
import nl.spellenweekend.app.data.MemberStatus
import nl.spellenweekend.app.data.Place

/** Snelle opzoektabellen die alle schermen delen. */
data class Lookup(
    val members: Map<String, Member> = emptyMap(),
    val games: Map<String, Game> = emptyMap(),
    val places: Map<String, Place> = emptyMap(),
    val tables: Map<String, GameTable> = emptyMap(),
    val rooms: Map<String, Room> = emptyMap(),
    val guests: List<Guest> = emptyList(),
    val chores: List<Chore> = emptyList(),
    val event: WeekendEvent? = null,
) {
    /** Gastnaam waaraan dit lid gekoppeld is (of de eigen naam). */
    fun guestName(member: Member?): String? =
        member?.guestId?.takeIf { it.isNotBlank() }?.let { gid -> guests.firstOrNull { it.id == gid }?.name }

    fun roomOf(member: Member?): Room? {
        val gid = member?.guestId?.takeIf { it.isNotBlank() } ?: return null
        val guest = guests.firstOrNull { it.id == gid } ?: return null
        return rooms[guest.roomId]
    }

    fun choresOf(member: Member?): List<Chore> {
        val n = guestName(member) ?: return emptyList()
        return chores.filter { n in it.names }.sortedBy { it.startAt }
    }

    fun placeName(id: String): String = places[id]?.name ?: ""

    fun name(uid: String): String = members[uid]?.name?.ifBlank { null } ?: "Onbekend"
    fun names(uids: List<String>): String = uids.joinToString(", ") { name(it) }

    fun whereText(a: Activity): String {
        val t = tables[a.tableId]
        val p = places[t?.placeId?.ifBlank { null } ?: a.placeId]
        return listOfNotNull(t?.name, p?.name).joinToString(" · ")
    }
}

fun typeIcon(t: ActivityType): ImageVector = when (t) {
    ActivityType.SPEL -> Icons.Filled.Casino
    ActivityType.ETEN -> Icons.Filled.Restaurant
    ActivityType.WANDELEN -> Icons.AutoMirrored.Filled.DirectionsWalk
    ActivityType.ANDERS -> Icons.Filled.Celebration
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier.padding(top = 20.dp, bottom = 8.dp),
    )
}

@Composable
fun StatusDot(s: MemberStatus, size: Dp = 10.dp) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(statusColor(s))
    )
}

@Composable
fun MemberPill(m: Member, modifier: Modifier = Modifier) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        ) {
            StatusDot(m.status, 8.dp)
            Spacer(Modifier.width(6.dp))
            Text(m.name, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun IconBadge(icon: ImageVector, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

@Composable
fun EmptyHint(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp, horizontal = 16.dp),
    )
}

/** Keuzeveld met uitklapmenu (key → label). */
@Composable
fun SelectField(
    label: String,
    value: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedCard(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        options.firstOrNull { it.first == value }?.second ?: "—",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                Icon(Icons.Filled.ArrowDropDown, null)
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (key, text) ->
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = {
                        onSelect(key)
                        open = false
                    },
                )
            }
        }
    }
}

/** Getal met − en + knoppen. */
@Composable
fun NumberField(
    label: String,
    value: Int,
    onChange: (Int) -> Unit,
    range: IntRange,
    step: Int = 1,
    display: (Int) -> String = { it.toString() },
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        IconButton(
            onClick = { onChange((value - step).coerceIn(range)) },
            enabled = value > range.first,
        ) { Icon(Icons.Filled.Remove, "Minder") }
        Text(
            display(value),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(72.dp),
        )
        IconButton(
            onClick = { onChange((value + step).coerceIn(range)) },
            enabled = value < range.last,
        ) { Icon(Icons.Filled.Add, "Meer") }
    }
}
