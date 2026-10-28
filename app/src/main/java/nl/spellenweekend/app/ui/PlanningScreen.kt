package nl.spellenweekend.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import nl.spellenweekend.app.data.Activity
import nl.spellenweekend.app.data.ActivityStatus

@Composable
fun PlanningScreen(
    vm: AppViewModel,
    me: String,
    look: Lookup,
    activities: List<Activity>,
    onEdit: (Activity) -> Unit,
) {
    var onlyMine by rememberSaveable { mutableStateOf(false) }
    var showDone by rememberSaveable { mutableStateOf(false) }

    val visible = activities
        .filter { !onlyMine || me in it.participants || me in it.invited }
        .filter { showDone || it.isOpen }
        .sortedBy { it.startAt }
    val byDay = visible.groupBy { toDate(it.startAt) }.toSortedMap()

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = onlyMine, onClick = { onlyMine = !onlyMine }, label = { Text("Alleen van mij") })
                    FilterChip(selected = showDone, onClick = { showDone = !showDone }, label = { Text("Ook afgelopen") })
                }
            }
            if (visible.isEmpty()) {
                item { EmptyHint("Niets gepland. Spellen, eten, een wandeling: tik op + om iets in te plannen.") }
            }
            byDay.forEach { (day, list) ->
                item(key = "day-$day") { SectionHeader(dayHeader(day)) }
                items(list, key = { it.id }) { a ->
                    ActivityCard(a, me, look, vm, onEdit)
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { onEdit(Activity(startAt = roundedNow())) },
            icon = { Icon(Icons.Filled.Add, null) },
            text = { Text("Plannen") },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
        )
    }
}
