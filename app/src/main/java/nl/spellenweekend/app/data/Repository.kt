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
     * Koppelt jou aan een naam uit de gastenlijst (kamer en corvee).
     * ♥'s uit het Slimbo-document worden niet overgenomen; alleen de ♥'s van je eigen oude toestel gaan mee.
     */
    suspend fun claimGuest(eventId: String, guest: Guest, previousGuestId: String) {
        val batch = db.batch()
        val member = col(eventId, "members").document(me)
        batch.update(
            member,
            mapOf("guestId" to guest.id, "name" to guest.name, "hidden" to false, "replacedBy" to "", "updatedAt" to now()),
        )
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
