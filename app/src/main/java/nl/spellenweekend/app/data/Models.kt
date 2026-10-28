package nl.spellenweekend.app.data

import com.google.firebase.firestore.DocumentSnapshot

enum class MemberStatus(val label: String) {
    BESCHIKBAAR("Beschikbaar"),
    RESERVEERBAAR("Reserveerbaar"),
    BEZET("Bezet");

    companion object {
        fun of(s: String?) = entries.firstOrNull { it.name == s } ?: BESCHIKBAAR
    }
}

enum class ActivityType(val label: String) {
    SPEL("Spel"),
    ETEN("Eten"),
    WANDELEN("Wandelen"),
    ANDERS("Anders");

    companion object {
        fun of(s: String?) = entries.firstOrNull { it.name == s } ?: ANDERS
    }
}

enum class ActivityStatus(val label: String) {
    GEPLAND("Gepland"),
    GESTART("Bezig"),
    AFGEROND("Afgerond"),
    GEANNULEERD("Geannuleerd");

    companion object {
        fun of(s: String?) = entries.firstOrNull { it.name == s } ?: GEPLAND
    }
}

enum class Weight(val label: String) {
    LICHT("Licht"),
    MIDDEL("Middel"),
    ZWAAR("Zwaar");

    companion object {
        fun of(s: String?) = entries.firstOrNull { it.name == s } ?: MIDDEL
    }
}

data class InfoSection(
    val title: String = "",
    val items: List<String> = emptyList(),
)

data class WeekendEvent(
    val id: String = "",
    val name: String = "",
    val joinCode: String = "",
    val createdBy: String = "",
    val venue: String = "",
    val address: String = "",
    val phone: String = "",
    val checkInAt: Long = 0L,
    val checkOutAt: Long = 0L,
    val info: List<InfoSection> = emptyList(),
    val seedVersion: Int = 0,
)

data class Member(
    val id: String = "",
    val name: String = "",
    val status: MemberStatus = MemberStatus.BESCHIKBAAR,
    val statusNote: String = "",
    val wants: List<String> = emptyList(),
    val updatedAt: Long = 0L,
    /** Gekoppelde naam uit de gastenlijst (kamerindeling) */
    val guestId: String = "",
)

data class Game(
    val id: String = "",
    val name: String = "",
    val minPlayers: Int = 2,
    val maxPlayers: Int = 4,
    val durationMin: Int = 60,
    val weight: Weight = Weight.MIDDEL,
    val owner: String = "",
    val notes: String = "",
    /** Op de wenslijst: nog niemand neemt het mee */
    val wishlist: Boolean = false,
    val link: String = "",
    val video: String = "",
    /** Namen uit de spreadsheet die interesse hebben (ook als ze de app nog niet gebruiken) */
    val interested: List<String> = emptyList(),
    /** false = spelersaantal/duur nog niet gecontroleerd (standaardwaarden) */
    val infoChecked: Boolean = true,
    /** Hoofdcategorie (Strategie, Familie, Party, …) */
    val category: String = "",
    /** Categorieën van BoardGameGeek (Engels), ter informatie */
    val categories: List<String> = emptyList(),
) {
    fun supports(players: Int) = players in minPlayers..maxPlayers
}

/** Vaste lijst hoofdcategorieën (afgeleid van de BGG-"subdomains"). */
val GAME_CATEGORIES = listOf(
    "Strategie", "Familie", "Party", "Thematisch", "Kaartspel", "Abstract", "Coöperatief", "Kinderen", "Oorlog", "Overig",
)


/** Slaapkamer met bewoners volgens de kamerindeling. */
data class Room(
    val id: String = "",
    val placeId: String = "",
    val floor: Int = 0,
    val name: String = "",
    val beds: Int = 0,
    val bedInfo: String = "",
    val occupants: List<String> = emptyList(),
)

/** Iemand uit de gastenlijst; wordt gekoppeld zodra die persoon de app gebruikt. */
data class Guest(
    val id: String = "",
    val name: String = "",
    val roomId: String = "",
    val wants: List<String> = emptyList(),
    val claimedBy: String = "",
)

/** Corveeblok (ochtend/middag/avond) per residentie. */
data class Chore(
    val id: String = "",
    val placeId: String = "",
    val day: String = "",
    val slot: String = "",
    val startAt: Long = 0L,
    val endAt: Long = 0L,
    val names: List<String> = emptyList(),
)

/** Een ruimte of plek: woonkamer, serre, zolder, restaurant… */
data class Place(
    val id: String = "",
    val name: String = "",
    val description: String = "",
)

data class GameTable(
    val id: String = "",
    val name: String = "",
    val placeId: String = "",
    val seats: Int = 4,
)

data class Activity(
    val id: String = "",
    val type: ActivityType = ActivityType.SPEL,
    val title: String = "",
    val gameId: String = "",
    val placeId: String = "",
    val tableId: String = "",
    val startAt: Long = 0L,
    val durationMin: Int = 60,
    /** 0 = geen maximum */
    val maxParticipants: Int = 0,
    val createdBy: String = "",
    val participants: List<String> = emptyList(),
    val invited: List<String> = emptyList(),
    val declined: List<String> = emptyList(),
    val status: ActivityStatus = ActivityStatus.GEPLAND,
    val notes: String = "",
    val reminderSent: Boolean = false,
    val updatedBy: String = "",
) {
    val endAt: Long get() = startAt + durationMin * 60_000L
    val isOpen: Boolean get() = status == ActivityStatus.GEPLAND || status == ActivityStatus.GESTART
    val isFull: Boolean get() = maxParticipants > 0 && participants.size >= maxParticipants
}

// ---------- Firestore <-> model (handmatig, zonder reflectie) ----------

private fun DocumentSnapshot.str(k: String): String = getString(k) ?: ""
private fun DocumentSnapshot.int(k: String, d: Int): Int = getLong(k)?.toInt() ?: d
private fun DocumentSnapshot.long(k: String): Long = getLong(k) ?: 0L
private fun DocumentSnapshot.strList(k: String): List<String> =
    (get(k) as? List<*>)?.filterIsInstance<String>() ?: emptyList()

fun DocumentSnapshot.toEvent() = WeekendEvent(
    id = id,
    name = str("name"),
    joinCode = str("joinCode"),
    createdBy = str("createdBy"),
    venue = str("venue"),
    address = str("address"),
    phone = str("phone"),
    checkInAt = long("checkInAt"),
    checkOutAt = long("checkOutAt"),
    info = (get("info") as? List<*>)?.mapNotNull { item ->
        (item as? Map<*, *>)?.let { m ->
            InfoSection(
                title = m["title"] as? String ?: "",
                items = (m["items"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
            )
        }
    } ?: emptyList(),
    seedVersion = int("seedVersion", 0),
)

fun DocumentSnapshot.toMember() = Member(
    id = id,
    name = str("name"),
    status = MemberStatus.of(getString("status")),
    statusNote = str("statusNote"),
    wants = strList("wants"),
    updatedAt = long("updatedAt"),
    guestId = str("guestId"),
)

fun DocumentSnapshot.toRoom() = Room(
    id = id,
    placeId = str("placeId"),
    floor = int("floor", 0),
    name = str("name"),
    beds = int("beds", 0),
    bedInfo = str("bedInfo"),
    occupants = strList("occupants"),
)

fun DocumentSnapshot.toGuest() = Guest(
    id = id,
    name = str("name"),
    roomId = str("roomId"),
    wants = strList("wants"),
    claimedBy = str("claimedBy"),
)

fun DocumentSnapshot.toChore() = Chore(
    id = id,
    placeId = str("placeId"),
    day = str("day"),
    slot = str("slot"),
    startAt = long("startAt"),
    endAt = long("endAt"),
    names = strList("names"),
)

fun DocumentSnapshot.toGame() = Game(
    id = id,
    name = str("name"),
    minPlayers = int("minPlayers", 2),
    maxPlayers = int("maxPlayers", 4),
    durationMin = int("durationMin", 60),
    weight = Weight.of(getString("weight")),
    owner = str("owner"),
    notes = str("notes"),
    wishlist = getBoolean("wishlist") ?: false,
    link = str("link"),
    video = str("video"),
    interested = strList("interested"),
    infoChecked = getBoolean("infoChecked") ?: true,
    category = str("category"),
    categories = strList("categories"),
)

fun DocumentSnapshot.toPlace() = Place(
    id = id,
    name = str("name"),
    description = str("description"),
)

fun DocumentSnapshot.toTable() = GameTable(
    id = id,
    name = str("name"),
    placeId = str("placeId"),
    seats = int("seats", 4),
)

fun DocumentSnapshot.toActivity() = Activity(
    id = id,
    type = ActivityType.of(getString("type")),
    title = str("title"),
    gameId = str("gameId"),
    placeId = str("placeId"),
    tableId = str("tableId"),
    startAt = long("startAt"),
    durationMin = int("durationMin", 60),
    maxParticipants = int("maxParticipants", 0),
    createdBy = str("createdBy"),
    participants = strList("participants"),
    invited = strList("invited"),
    declined = strList("declined"),
    status = ActivityStatus.of(getString("status")),
    notes = str("notes"),
    reminderSent = getBoolean("reminderSent") ?: false,
    updatedBy = str("updatedBy"),
)

fun Game.toMap(): Map<String, Any> = mapOf(
    "name" to name,
    "minPlayers" to minPlayers,
    "maxPlayers" to maxPlayers,
    "durationMin" to durationMin,
    "weight" to weight.name,
    "owner" to owner,
    "notes" to notes,
    "wishlist" to wishlist,
    "link" to link,
    "video" to video,
    "interested" to interested,
    "infoChecked" to infoChecked,
    "category" to category,
)

fun Place.toMap(): Map<String, Any> = mapOf(
    "name" to name,
    "description" to description,
)

fun GameTable.toMap(): Map<String, Any> = mapOf(
    "name" to name,
    "placeId" to placeId,
    "seats" to seats,
)

/** Velden die bij bewerken worden overschreven (deelnemers blijven buiten schot). */
fun Activity.editableMap(): Map<String, Any> = mapOf(
    "type" to type.name,
    "title" to title,
    "gameId" to gameId,
    "placeId" to placeId,
    "tableId" to tableId,
    "startAt" to startAt,
    "durationMin" to durationMin,
    "maxParticipants" to maxParticipants,
    "invited" to invited,
    "declined" to declined,
    "notes" to notes,
    "reminderSent" to reminderSent,
)
