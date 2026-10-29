package nl.spellenweekend.app.data

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject

private const val TAG = "Repository"

/**
 * Alle data staat in Firestore onder events/{eventId}/...
 *   members/{uid}       naam, status, wil-spelen-lijst, push-token
 *   games/{id}          spellen
 *   places/{id}         locaties / ruimtes
 *   tables/{id}         tafels (horen bij een locatie)
 *   activities/{id}     geplande spellen, eten, wandelen, …
 */
class Repository(context: Context) {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val prefs = context.applicationContext
        .getSharedPreferences("spellenweekend", Context.MODE_PRIVATE)

    val uid: String? get() = auth.currentUser?.uid
    private val me: String get() = uid ?: error("Niet ingelogd")

    var eventId: String?
        get() = prefs.getString("eventId", null)
        set(value) {
            val e = prefs.edit()
            if (value == null) e.remove("eventId") else e.putString("eventId", value)
            e.apply()
        }

    private fun now() = System.currentTimeMillis()

    suspend fun signIn(): String {
        val current = auth.currentUser
        if (current != null) return current.uid
        val result = auth.signInAnonymously().await()
        return result.user?.uid ?: error("Inloggen mislukt")
    }

    private fun event(id: String) = db.collection("events").document(id)
    private fun col(eventId: String, name: String): CollectionReference =
        event(eventId).collection(name)

    // ---------------- Weekend aanmaken / meedoen ----------------

    suspend fun createEvent(name: String, userName: String): String {
        val ref = db.collection("events").document()
        ref.set(
            mapOf(
                "name" to name,
                "joinCode" to newJoinCode(),
                "createdBy" to me,
                "createdAt" to now(),
            )
        ).await()
        joinAsMember(ref.id, userName)
        return ref.id
    }

    suspend fun joinByCode(code: String, userName: String): String {
        val snap = db.collection("events")
            .whereEqualTo("joinCode", code.trim().uppercase())
            .limit(1)
            .get()
            .await()
        val doc = snap.documents.firstOrNull()
            ?: throw IllegalArgumentException("Geen weekend gevonden met code ${code.uppercase()}")
        joinAsMember(doc.id, userName)
        return doc.id
    }

    private suspend fun joinAsMember(eventId: String, name: String) {
        col(eventId, "members").document(me).set(
            mapOf(
                "name" to name.trim(),
                "status" to MemberStatus.BESCHIKBAAR.name,
                "updatedAt" to now(),
            ),
            SetOptions.merge()
        ).await()
    }

    /** Momentopname van alles wat nodig is voor de meldingen-controle. */
    data class Snapshot(
        val activities: List<Activity>,
        val members: List<Member>,
        val places: List<Place>,
        val tables: List<GameTable>,
    )

    /**
     * Haalt de actuele gegevens rechtstreeks van de server (Vernieuwen-knop en de
     * achtergrondcontrole). Gooit een fout als er geen verbinding is.
     */
    suspend fun fetchAll(eventId: String, fromServer: Boolean = true): Snapshot {
        val src = if (fromServer) Source.SERVER else Source.DEFAULT
        suspend fun get(name: String) = col(eventId, name).get(src).await().documents
        return Snapshot(
            activities = get("activities").map { it.toActivity() },
            members = get("members").filter { it.getBoolean("web") != true && it.getBoolean("hidden") != true }.map { it.toMember() },
            places = get("places").map { it.toPlace() },
            tables = get("tables").map { it.toTable() },
        )
    }

    /**
     * Meldt app-versie en "laatst gezien" aan de database (voor de beheerwebsite).
     * [opened] = de app is geopend of vernieuwd; anders alleen een achtergrondcontrole.
     */
    suspend fun reportPresence(eventId: String, context: Context, opened: Boolean) {
        val u = uid ?: return
        val data = DeviceInfo.fields(context) +
            (if (opened) mapOf("lastOpenedAt" to now()) else emptyMap()) +
            mapOf("lastSeenAt" to now())
        runCatching {
            col(eventId, "members").document(u).set(data, SetOptions.merge()).await()
        }.onFailure { Log.w(TAG, "presence", it) }
    }

    private fun newJoinCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" // geen 0/O/1/I
        return (1..6).map { chars.random() }.joinToString("")
    }

    // ---------------- Live data ----------------

    fun observeEvent(id: String): Flow<WeekendEvent?> = callbackFlow {
        val reg = event(id).addSnapshotListener { snap, err ->
            if (err != null) {
                Log.w(TAG, "event", err); return@addSnapshotListener
            }
            trySend(snap?.takeIf { it.exists() }?.toEvent())
        }
        awaitClose { reg.remove() }
    }

    /** Leden van de beheerwebsite (web) en vervangen oude toestellen (hidden) tellen niet mee in de app. */
    fun members(eventId: String): Flow<List<Member>> = callbackFlow {
        val reg = col(eventId, "members").addSnapshotListener { snap, err ->
            if (err != null) {
                Log.w(TAG, "members", err); return@addSnapshotListener
            }
            if (snap != null) trySend(snap.documents.filter { it.getBoolean("web") != true && it.getBoolean("hidden") != true }.map { it.toMember() })
        }
        awaitClose { reg.remove() }
    }
    fun games(eventId: String) = col(eventId, "games").orderBy("name").observe { it.toGame() }
    fun places(eventId: String) = col(eventId, "places").orderBy("name").observe { it.toPlace() }
    fun tables(eventId: String) = col(eventId, "tables").orderBy("name").observe { it.toTable() }
    fun activities(eventId: String) =
        col(eventId, "activities").orderBy("startAt").observe { it.toActivity() }
    fun rooms(eventId: String) = col(eventId, "rooms").observe { it.toRoom() }
    fun guests(eventId: String) = col(eventId, "guests").orderBy("name").observe { it.toGuest() }
    fun chores(eventId: String) = col(eventId, "chores").orderBy("startAt").observe { it.toChore() }

    private fun <T> Query.observe(map: (DocumentSnapshot) -> T): Flow<List<T>> = callbackFlow {
        val reg = addSnapshotListener { snap, err ->
            if (err != null) {
                Log.w(TAG, "listen", err); return@addSnapshotListener
            }
            if (snap != null) trySend(snap.documents.map(map))
        }
        awaitClose { reg.remove() }
    }

    // ---------------- Leden ----------------

    suspend fun setMyStatus(eventId: String, status: MemberStatus, note: String) {
        col(eventId, "members").document(me).update(
            mapOf(
                "status" to status.name,
                "statusNote" to note.trim(),
                "updatedAt" to now(),
            )
        ).await()
    }

    suspend fun renameMe(eventId: String, name: String) {
        col(eventId, "members").document(me).update("name", name.trim()).await()
    }

    suspend fun setWant(eventId: String, gameId: String, want: Boolean) {
        val value = if (want) FieldValue.arrayUnion(gameId) else FieldValue.arrayRemove(gameId)
        col(eventId, "members").document(me).update("wants", value).await()
    }

    /**
     * Koppelt jou aan een naam uit de gastenlijst: je naam wordt overgenomen en
     * de spellen waar je in de spreadsheet interesse in had, komen op je ♥-lijst.
     */
    suspend fun claimGuest(eventId: String, guest: Guest, previousGuestId: String) {
        val batch = db.batch()
        val member = col(eventId, "members").document(me)
        batch.update(
            member,
            mapOf("guestId" to guest.id, "name" to guest.name, "hidden" to false, "replacedBy" to "", "updatedAt" to now()),
        )
        if (guest.wants.isNotEmpty()) {
            batch.update(member, "wants", FieldValue.arrayUnion(*guest.wants.toTypedArray()))
        }
        batch.update(col(eventId, "guests").document(guest.id), "claimedBy", me)
        if (previousGuestId.isNotBlank() && previousGuestId != guest.id) {
            batch.update(col(eventId, "guests").document(previousGuestId), "claimedBy", "")
        }
        // Naam was gekoppeld aan een ander (oud) toestel: ♥'s overnemen en dat account verbergen
        val old = guest.claimedBy
        if (old.isNotBlank() && old != me) {
            val oldDoc = runCatching { col(eventId, "members").document(old).get().await() }.getOrNull()
            val oldWants = (oldDoc?.get("wants") as? List<*>)?.filterIsInstance<String>().orEmpty()
            if (oldWants.isNotEmpty()) {
                batch.update(member, "wants", FieldValue.arrayUnion(*oldWants.toTypedArray()))
            }
            if (oldDoc?.exists() == true) {
                batch.update(oldDoc.reference, mapOf("hidden" to true, "replacedBy" to me, "guestId" to ""))
            }
        }
        batch.commit().await()
    }

    /**
     * Laadt het importbestand (assets/seed_*.json) in het weekend.
     * Locaties en tafels die al bestaan blijven ongemoeid. Bij spellen worden alleen
     * eigenaar, wenslijst, interesse en links bijgewerkt; spellen die uit het document
     * verdwenen zijn worden verwijderd. Kamers, gastenlijst, corvee en weekendinfo
     * worden volledig bijgewerkt.
     */
    suspend fun importSeed(eventId: String, json: String): String {
        val root = JSONObject(json)
        val existing = mutableMapOf<String, Set<String>>()
        for (c in listOf("games", "places", "tables", "activities", "guests")) {
            existing[c] = col(eventId, c).get().await().documents.map { it.id }.toSet()
        }
        val ops = mutableListOf<Pair<com.google.firebase.firestore.DocumentReference, Map<String, Any>>>()

        val ev = root.getJSONObject("event")
        ops += event(eventId) to mapOf(
            "venue" to ev.optString("venue"),
            "address" to ev.optString("address"),
            "phone" to ev.optString("phone"),
            "checkInAt" to ev.optLong("checkInAt"),
            "checkOutAt" to ev.optLong("checkOutAt"),
            "info" to toList(ev.optJSONArray("info")),
            "seedVersion" to root.optInt("version", 1),
        )

        var newGames = 0
        var updatedGames = 0
        fun addAll(collection: String, onlyNew: Boolean, transform: (Map<String, Any>) -> Map<String, Any> = { it }) {
            val arr = root.optJSONArray(collection) ?: return
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val id = obj.getString("id")
                if (onlyNew && id in existing[collection].orEmpty()) continue
                @Suppress("UNCHECKED_CAST")
                val data = (toValue(obj) as Map<String, Any>) - "id"
                ops += col(eventId, collection).document(id) to transform(data)
                if (collection == "games") newGames++
            }
        }

        // Spellen: nieuwe toevoegen; bij bestaande alleen de velden uit het document
        // bijwerken (eigenaar, wenslijst, interesse, links). Spelersaantal, duur,
        // zwaarte en notities die in de app zijn aangepast blijven staan.
        val seedGames = root.optJSONArray("games")
        val seedGameIds = mutableSetOf<String>()
        // Spellen waarvan spelers/duur nog niet in de app zijn gecontroleerd of aangepast
        val gameDocs = col(eventId, "games").get().await().documents
        val uncheckedGames = gameDocs.filter { it.getBoolean("infoChecked") == false }.map { it.id }.toSet()
        val uncategorized = gameDocs.filter { it.getString("category").isNullOrBlank() }.map { it.id }.toSet()
        val gameById = gameDocs.associateBy { it.id }
        if (seedGames != null) {
            for (i in 0 until seedGames.length()) {
                val obj = seedGames.getJSONObject(i)
                val id = obj.getString("id")
                seedGameIds += id
                @Suppress("UNCHECKED_CAST")
                val data = (toValue(obj) as Map<String, Any>) - "id"
                val ref = col(eventId, "games").document(id)
                if (id in existing["games"].orEmpty()) {
                    // Naam, eigenaar en link uit de app blijven staan; eigenaar alleen overnemen
                    // als het spreadsheet er een noemt, nooit wissen.
                    val cur = gameById[id]
                    val fields = mutableSetOf("interested", "video", "categories")
                    val curLink = cur?.getString("link").orEmpty()
                    if (curLink.isBlank() || "geeksearch" in curLink) fields += "link"
                    val seedOwner = obj.optString("owner")
                    var dataOut = data
                    if (seedOwner.isNotBlank()) {
                        fields += setOf("owner", "wishlist"); dataOut = data + ("wishlist" to false)
                    } else if (cur?.getString("owner").isNullOrBlank()) {
                        fields += "wishlist"
                    }
                    if (id in uncategorized) fields += "category"
                    val curNotes = cur?.getString("notes").orEmpty()
                    if (curNotes.isBlank() || curNotes.startsWith("ⓘ ") || curNotes == "Wens: nog niemand neemt dit mee") fields += "notes"
                    // BGG-gegevens alleen overnemen als niemand ze in de app heeft aangepast
                    if (id in uncheckedGames && obj.optBoolean("infoChecked", false)) {
                        fields += setOf("minPlayers", "maxPlayers", "durationMin", "weight", "infoChecked")
                    }
                    ops += ref to dataOut.filterKeys { it in fields }
                    updatedGames++
                } else {
                    ops += ref to data
                    newGames++
                }
            }
        }
        // Spellen uit een eerdere import die niet meer in het document staan (bv. hernoemd).
        // Zelf in de app toegevoegde spellen hebben een willekeurige id zonder "g-" en blijven staan.
        val staleGames = existing["games"].orEmpty().filter { it.startsWith("g-") && it !in seedGameIds }

        addAll("places", onlyNew = true)
        addAll("tables", onlyNew = true)
        addAll("rooms", onlyNew = false)
        addAll("chores", onlyNew = false)
        addAll("guests", onlyNew = false)

        // Activiteiten uit het document: nieuwe toevoegen, bestaande krijgen titel/tijd/notitie bij
        val seedActs = root.optJSONArray("activities")
        if (seedActs != null) {
            for (i in 0 until seedActs.length()) {
                val obj = seedActs.getJSONObject(i)
                val id = obj.getString("id")
                @Suppress("UNCHECKED_CAST")
                val data = (toValue(obj) as Map<String, Any>) - "id"
                val ref = col(eventId, "activities").document(id)
                ops += ref to if (id in existing["activities"].orEmpty()) {
                    data.filterKeys { it in setOf("title", "type", "startAt", "durationMin", "notes") } +
                        mapOf("updatedBy" to me, "updatedAt" to now())
                } else {
                    data + mapOf(
                        "createdBy" to me,
                        "participants" to emptyList<String>(),
                        "invited" to emptyList<String>(),
                        "declined" to emptyList<String>(),
                        "status" to ActivityStatus.GEPLAND.name,
                        "maxParticipants" to 0,
                        "tableId" to "",
                        "gameId" to "",
                        "reminderSent" to false,
                        "updatedBy" to me,
                        "updatedAt" to now(),
                    )
                }
            }
        }

        // Leden die al aan een naam gekoppeld zijn krijgen nieuwe interesse op hun ♥-lijst
        val claimed = col(eventId, "guests").get().await().documents
            .map { it.toGuest() }.filter { it.claimedBy.isNotBlank() }.associateBy { it.id }
        val seedGuests = root.optJSONArray("guests")
        if (seedGuests != null) {
            for (i in 0 until seedGuests.length()) {
                val obj = seedGuests.getJSONObject(i)
                val g = claimed[obj.getString("id")] ?: continue
                val wants = obj.optJSONArray("wants")?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty()
                if (wants.isEmpty()) continue
                ops += col(eventId, "members").document(g.claimedBy) to
                    mapOf("wants" to FieldValue.arrayUnion(*wants.toTypedArray()))
            }
        }

        // Firestore: max. 500 schrijfacties per batch
        ops.chunked(400).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { (ref, data) -> batch.set(ref, data, SetOptions.merge()) }
            batch.commit().await()
        }
        staleGames.chunked(400).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { batch.delete(col(eventId, "games").document(it)) }
            batch.commit().await()
        }
        val rooms = root.optJSONArray("rooms")?.length() ?: 0
        val guests = root.optJSONArray("guests")?.length() ?: 0
        return "Geladen: $newGames nieuwe en $updatedGames bijgewerkte spellen, $rooms kamers, $guests gasten, corvee en weekendinfo"
    }

    private fun toValue(v: Any?): Any = when (v) {
        is JSONObject -> v.keys().asSequence().associateWith { toValue(v.get(it)) }
        is JSONArray -> toList(v)
        JSONObject.NULL, null -> ""
        else -> v
    }

    private fun toList(arr: JSONArray?): List<Any> =
        if (arr == null) emptyList() else (0 until arr.length()).map { toValue(arr.get(it)) }

    // ---------------- Spellen, locaties, tafels ----------------

    suspend fun saveGame(eventId: String, g: Game) = save(col(eventId, "games"), g.id, g.toMap())
    suspend fun savePlace(eventId: String, p: Place) = save(col(eventId, "places"), p.id, p.toMap())
    suspend fun saveTable(eventId: String, t: GameTable) = save(col(eventId, "tables"), t.id, t.toMap())

    suspend fun delete(eventId: String, collection: String, id: String) {
        col(eventId, collection).document(id).delete().await()
    }

    private suspend fun save(c: CollectionReference, id: String, data: Map<String, Any>) {
        if (id.isBlank()) c.add(data).await()
        else c.document(id).set(data, SetOptions.merge()).await()
    }

    // ---------------- Activiteiten ----------------

    suspend fun saveActivity(eventId: String, a: Activity) {
        val c = col(eventId, "activities")
        val base = a.editableMap() + mapOf("updatedBy" to me, "updatedAt" to now())
        if (a.id.isBlank()) {
            val data = base + mapOf(
                "createdBy" to me,
                "participants" to (a.participants + me).distinct(),
                "status" to ActivityStatus.GEPLAND.name,
            )
            c.add(data).await()
        } else {
            c.document(a.id).set(base, SetOptions.merge()).await()
        }
    }

    suspend fun respond(eventId: String, activityId: String, accept: Boolean) {
        val target = if (accept) "participants" else "declined"
        col(eventId, "activities").document(activityId).update(
            mapOf(
                "invited" to FieldValue.arrayRemove(me),
                target to FieldValue.arrayUnion(me),
                "updatedBy" to me,
                "updatedAt" to now(),
            )
        ).await()
    }

    suspend fun join(eventId: String, activityId: String) {
        col(eventId, "activities").document(activityId).update(
            mapOf(
                "participants" to FieldValue.arrayUnion(me),
                "invited" to FieldValue.arrayRemove(me),
                "declined" to FieldValue.arrayRemove(me),
                "updatedBy" to me,
                "updatedAt" to now(),
            )
        ).await()
    }

    suspend fun leave(eventId: String, activityId: String) {
        col(eventId, "activities").document(activityId).update(
            mapOf(
                "participants" to FieldValue.arrayRemove(me),
                "updatedBy" to me,
                "updatedAt" to now(),
            )
        ).await()
    }

    /**
     * Status van een activiteit wijzigen. Bij starten worden alle deelnemers op
     * "Bezet" gezet, bij afronden/annuleren van een lopende activiteit weer op "Beschikbaar".
     */
    suspend fun setActivityStatus(eventId: String, a: Activity, status: ActivityStatus) {
        val batch = db.batch()
        batch.update(
            col(eventId, "activities").document(a.id),
            mapOf("status" to status.name, "updatedBy" to me, "updatedAt" to now())
        )
        val members = col(eventId, "members")
        when {
            status == ActivityStatus.GESTART -> a.participants.forEach { p ->
                batch.update(
                    members.document(p),
                    mapOf(
                        "status" to MemberStatus.BEZET.name,
                        "statusNote" to a.title,
                        "updatedAt" to now(),
                    )
                )
            }
            a.status == ActivityStatus.GESTART -> a.participants.forEach { p ->
                batch.update(
                    members.document(p),
                    mapOf(
                        "status" to MemberStatus.BESCHIKBAAR.name,
                        "statusNote" to "",
                        "updatedAt" to now(),
                    )
                )
            }
        }
        batch.commit().await()
    }
}
