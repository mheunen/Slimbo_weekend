@file:OptIn(ExperimentalMaterial3Api::class)

package nl.spellenweekend.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.IconButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import nl.spellenweekend.app.data.Activity

enum class MainTab(val label: String, val icon: ImageVector) {
    NU("Nu", Icons.Filled.Today),
    PLANNING("Planning", Icons.Filled.Event),
    SPELLEN("Spellen", Icons.Filled.Casino),
    MATCH("Match", Icons.Filled.AutoAwesome),
    MEER("Meer", Icons.Filled.Menu),
}

@Composable
fun AppRoot(vm: AppViewModel = viewModel()) {
    val phase by vm.phase.collectAsStateWithLifecycle()
    when (val p = phase) {
        Phase.Loading -> LoadingScreen()
        is Phase.Failed -> FailedScreen(p.message, vm::start)
        Phase.Onboarding -> OnboardingScreen(vm)
        Phase.Ready -> {
            NotificationPermission()
            MainScreen(vm)
        }
    }
}

@Composable
private fun NotificationPermission() {
    if (Build.VERSION.SDK_INT < 33) return
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}

@Composable
fun MainScreen(vm: AppViewModel) {
    val event by vm.event.collectAsStateWithLifecycle()
    val me by vm.myId.collectAsStateWithLifecycle()
    val members by vm.members.collectAsStateWithLifecycle()
    val games by vm.games.collectAsStateWithLifecycle()
    val places by vm.places.collectAsStateWithLifecycle()
    val tables by vm.tables.collectAsStateWithLifecycle()
    val activities by vm.activities.collectAsStateWithLifecycle()
    val rooms by vm.rooms.collectAsStateWithLifecycle()
    val guests by vm.guests.collectAsStateWithLifecycle()
    val chores by vm.chores.collectAsStateWithLifecycle()
    val msg by vm.message.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val lastRefresh by vm.lastRefresh.collectAsStateWithLifecycle()

    val look = remember(members, games, places, tables, rooms, guests, chores, event) {
        Lookup(
            members = members.associateBy { it.id },
            games = games.associateBy { it.id },
            places = places.associateBy { it.id },
            tables = tables.associateBy { it.id },
            rooms = rooms.associateBy { it.id },
            guests = guests,
            chores = chores,
            event = event,
        )
    }

    var tab by rememberSaveable { mutableStateOf(MainTab.NU) }
    var editing by remember { mutableStateOf<Activity?>(null) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(msg) {
        msg?.let { snackbar.showSnackbar(it); vm.clearMessage() }
    }

    val openInvites = activities.count { me in it.invited && it.isOpen }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(event?.name ?: "Spellenweekend", style = MaterialTheme.typography.titleLarge)
                        Text(
                            tab.label + if (lastRefresh > 0L) " · bijgewerkt ${fmtTime(lastRefresh)}" else "",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { vm.refresh() }, enabled = !refreshing) {
                        if (refreshing) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Filled.Refresh, "Vernieuwen")
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                MainTab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        label = { Text(t.label) },
                        icon = {
                            if (t == MainTab.NU && openInvites > 0) {
                                BadgedBox(badge = { Badge { Text("$openInvites") } }) { Icon(t.icon, null) }
                            } else {
                                Icon(t.icon, null)
                            }
                        },
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(pad)
        ) {
            val onEdit: (Activity) -> Unit = { editing = it }
            when (tab) {
                MainTab.NU -> NowScreen(vm, me, look, members, activities, onEdit, onOpenTab = { tab = it })
                MainTab.PLANNING -> PlanningScreen(vm, me, look, activities, onEdit)
                MainTab.SPELLEN -> GamesScreen(vm, me, members, games, guests)
                MainTab.MATCH -> MatchScreen(vm, me, look, members, games, tables, activities, onEdit)
                MainTab.MEER -> MoreScreen(vm, me, event, look, members, places, tables, activities, rooms, guests, chores)
            }
        }
    }

    editing?.let { a ->
        ActivityEditor(
            initial = a,
            me = me,
            look = look,
            members = members,
            onDismiss = { editing = null },
            onSave = {
                vm.saveActivity(it)
                editing = null
            },
        )
    }
}
