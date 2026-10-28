package nl.spellenweekend.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import nl.spellenweekend.app.data.Activity
import nl.spellenweekend.app.data.Chore
import nl.spellenweekend.app.data.Guest
import nl.spellenweekend.app.data.Room
import nl.spellenweekend.app.data.ActivityStatus
import nl.spellenweekend.app.data.Game
import nl.spellenweekend.app.data.GameTable
import nl.spellenweekend.app.data.Member
import nl.spellenweekend.app.data.MemberStatus
import nl.spellenweekend.app.data.Place
import nl.spellenweekend.app.data.Repository
import nl.spellenweekend.app.data.WeekendEvent
import nl.spellenweekend.app.notify.ChangeChecker
import nl.spellenweekend.app.notify.CheckWorker
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop

sealed interface Phase {
    data object Loading : Phase
    data class Failed(val message: String) : Phase
    data object Onboarding : Phase
    data object Ready : Phase
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = Repository(app)

    val phase = MutableStateFlow<Phase>(Phase.Loading)
    val myId = MutableStateFlow("")
    val busy = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)
    private val eventId = MutableStateFlow<String?>(null)

    val event: StateFlow<WeekendEvent?> = eventId
        .flatMapLatest { id -> if (id == null) flowOf(null) else repo.observeEvent(id) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val members: StateFlow<List<Member>> = live { repo.members(it) }
    val games: StateFlow<List<Game>> = live { repo.games(it) }
    val places: StateFlow<List<Place>> = live { repo.places(it) }
    val tables: StateFlow<List<GameTable>> = live { repo.tables(it) }
    val activities: StateFlow<List<Activity>> = live { repo.activities(it) }
    val rooms: StateFlow<List<Room>> = live { repo.rooms(it) }
    val guests: StateFlow<List<Guest>> = live { repo.guests(it) }
    val chores: StateFlow<List<Chore>> = live { repo.chores(it) }
    val importing = MutableStateFlow(false)

    private val checker = ChangeChecker(app)
    val refreshing = MutableStateFlow(false)
    /** Tijdstip van de laatste geslaagde controle bij de server (0 = nog nooit). */
    val lastRefresh = MutableStateFlow(0L)

    private fun <T> live(source: (String) -> Flow<List<T>>): StateFlow<List<T>> = eventId
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else source(id) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        start()
        // Terwijl de app open is komen wijzigingen live binnen; ook dan meldingen maken.
        viewModelScope.launch {
            combine(activities, members, places, tables) { a, m, p, t ->
                Repository.Snapshot(a, m, p, t)
            }
                .drop(1)
                .debounce(1_500)
                .collect { snap ->
                    val ev = eventId.value ?: return@collect
                    val me = myId.value
                    if (me.isBlank() || snap.members.isEmpty()) return@collect
                    checker.check(ev, me, snap)
                    lastRefresh.value = System.currentTimeMillis()
                }
        }
    }

    /** Vernieuwen-knop: alles opnieuw van de server halen en op wijzigingen controleren. */
    fun refresh() {
        val ev = eventId.value ?: return
        if (refreshing.value) return
        refreshing.value = true
        viewModelScope.launch {
            try {
                val snap = repo.fetchAll(ev)
                checker.check(ev, myId.value, snap)
                lastRefresh.value = System.currentTimeMillis()
                message.value = "Bijgewerkt om ${fmtTime(lastRefresh.value)}"
            } catch (e: Exception) {
                message.value = "Geen verbinding met de server — je ziet de laatst bekende gegevens"
            } finally {
                refreshing.value = false
            }
        }
    }

    fun start() {
        phase.value = Phase.Loading
        viewModelScope.launch {
            try {
                myId.value = repo.signIn()
                val ev = repo.eventId
                eventId.value = ev
                phase.value = if (ev == null) Phase.Onboarding else Phase.Ready
                if (ev != null) {
                    lastRefresh.value = checker.lastCheck(ev)
                    CheckWorker.schedule(getApplication())
                }
            } catch (e: Exception) {
                phase.value = Phase.Failed(e.message ?: "Kan geen verbinding maken")
            }
        }
    }

    fun clearMessage() {
        message.value = null
    }

    // ---------- onboarding ----------

    fun createEvent(name: String, userName: String) = onboarding {
        repo.createEvent(name.trim(), userName)
    }

    fun joinEvent(code: String, userName: String) = onboarding {
        repo.joinByCode(code, userName)
    }

    private fun onboarding(block: suspend () -> String) {
        if (busy.value) return
        busy.value = true
        viewModelScope.launch {
            try {
                val id = block()
                repo.eventId = id
                eventId.value = id
                phase.value = Phase.Ready
                CheckWorker.schedule(getApplication())
            } catch (e: Exception) {
                message.value = e.message ?: "Er ging iets mis"
            } finally {
                busy.value = false
            }
        }
    }

    fun leaveEvent() {
        CheckWorker.cancel(getApplication())
        repo.eventId = null
        eventId.value = null
        phase.value = Phase.Onboarding
    }

    // ---------- acties binnen het weekend ----------

    private fun act(done: String? = null, block: suspend (String) -> Unit) {
        val ev = eventId.value ?: return
        viewModelScope.launch {
            try {
                block(ev)
                if (done != null) message.value = done
            } catch (e: Exception) {
                message.value = e.message ?: "Er ging iets mis"
            }
        }
    }

    fun setStatus(status: MemberStatus, note: String) = act { repo.setMyStatus(it, status, note) }
    fun rename(name: String) = act("Naam opgeslagen") { repo.renameMe(it, name) }
    fun setWant(gameId: String, want: Boolean) = act { repo.setWant(it, gameId, want) }

    fun saveGame(g: Game) = act { repo.saveGame(it, g) }
    fun deleteGame(id: String) = act("Spel verwijderd") { repo.delete(it, "games", id) }
    fun savePlace(p: Place) = act { repo.savePlace(it, p) }
    fun deletePlace(id: String) = act("Locatie verwijderd") { repo.delete(it, "places", id) }
    fun saveTable(t: GameTable) = act { repo.saveTable(it, t) }
    fun deleteTable(id: String) = act("Tafel verwijderd") { repo.delete(it, "tables", id) }

    fun saveActivity(a: Activity) = act(if (a.id.isBlank()) "Ingepland" else "Opgeslagen") {
        repo.saveActivity(it, a)
    }
    fun deleteActivity(id: String) = act("Verwijderd") { repo.delete(it, "activities", id) }
    fun respond(a: Activity, accept: Boolean) = act { repo.respond(it, a.id, accept) }
    fun join(a: Activity) = act { repo.join(it, a.id) }
    fun leave(a: Activity) = act { repo.leave(it, a.id) }
    fun setActivityStatus(a: Activity, s: ActivityStatus) = act { repo.setActivityStatus(it, a, s) }

    fun claimGuest(g: Guest, previousGuestId: String) =
        act("Gekoppeld aan ${g.name}") { repo.claimGuest(it, g, previousGuestId) }

    /** Laadt de gegevens uit de bijlage (Molen van Medael, okt/nov 2026). */
    fun importSeed() {
        if (importing.value || event.value == null) return
        importing.value = true
        act {
            try {
                val json = getApplication<Application>().assets
                    .open(SEED_FILE).bufferedReader().use { r -> r.readText() }
                message.value = repo.importSeed(it, json)
            } finally {
                importing.value = false
            }
        }
    }

    companion object {
        const val SEED_FILE = "seed_medael_2026.json"
    }
}
