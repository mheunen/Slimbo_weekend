package nl.spellenweekend.app.notify

import android.content.Context
import nl.spellenweekend.app.data.Activity
import nl.spellenweekend.app.data.ActivityStatus
import nl.spellenweekend.app.data.Repository
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Vervangt de server-pushmeldingen: de telefoon vergelijkt zelf de huidige
 * activiteiten met wat hij de vorige keer zag, en maakt daar meldingen van.
 *
 * Wordt aangeroepen door de achtergrondcontrole (elke ~15 min), door de
 * Vernieuwen-knop en telkens als er live nieuwe gegevens binnenkomen terwijl
 * de app open is.
 */
class ChangeChecker(context: Context) {

    private val ctx = context.applicationContext
    private val prefs = ctx.getSharedPreferences("meldingen", Context.MODE_PRIVATE)

    /** Hoe ver van tevoren een herinnering komt. */
    private val reminderWindowMs = 20 * 60_000L

    @Synchronized
    fun check(eventId: String, me: String, snap: Repository.Snapshot, now: Long = System.currentTimeMillis()) {
        val names = snap.members.associate { it.id to it.name.ifBlank { "Iemand" } }
        val places = snap.places.associate { it.id to it.name }
        val tables = snap.tables.associate { it.id to (it.name to it.placeId) }

        fun name(uid: String) = names[uid] ?: "Iemand"
        fun where(a: Activity): String {
            val t = tables[a.tableId]
            val place = places[t?.second?.ifBlank { null } ?: a.placeId]
            return listOfNotNull(t?.first, place).joinToString(", ")
        }
        fun whenText(a: Activity) = "${day(a.startAt)} ${time(a.startAt)}"
        fun withWhere(text: String, a: Activity) = where(a).let { if (it.isBlank()) text else "$text · $it" }

        val stateKey = "state_$eventId"
        val baselineKey = "baseline_$eventId"
        val remindedKey = "reminded_$eventId"

        val previous = runCatching { JSONObject(prefs.getString(stateKey, "{}") ?: "{}") }.getOrDefault(JSONObject())
        val firstRun = !prefs.getBoolean(baselineKey, false)
        val reminded = prefs.getStringSet(remindedKey, emptySet())!!.toMutableSet()
        val out = mutableListOf<Notifier.Message>()

        for (a in snap.activities) {
            val involved = me in a.participants || me in a.invited
            val byOther = a.updatedBy.isNotBlank() && a.updatedBy != me
            val prev = previous.optJSONObject(a.id)

            if (!firstRun) {
                val prevInvited = prev?.optString("invited")?.split(",")?.toSet() ?: emptySet()
                val prevParticipants = prev?.optString("participants")?.split(",")?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
                val prevStatus = prev?.optString("status")
                val prevStart = prev?.optLong("startAt") ?: a.startAt

                // 1. Uitnodiging
                if (me in a.invited && me !in prevInvited && a.isOpen) {
                    out += Notifier.Message(
                        a.id, "invite", "Uitnodiging: ${a.title}",
                        withWhere("${name(a.createdBy)} nodigt je uit · ${whenText(a)}", a),
                    )
                }
                if (prev != null && involved && byOther) {
                    // 2. Gestart / geannuleerd
                    if (prevStatus != a.status.name) {
                        when (a.status) {
                            ActivityStatus.GESTART -> out += Notifier.Message(
                                a.id, "start", "${a.title} begint nu!",
                                withWhere("Gestart door ${name(a.updatedBy)}", a),
                            )
                            ActivityStatus.GEANNULEERD -> out += Notifier.Message(
                                a.id, "cancel", "Geannuleerd: ${a.title}",
                                "${name(a.updatedBy)} heeft het afgeblazen.",
                            )
                            else -> {}
                        }
                    }
                    // 3. Tijd verschoven
                    if (a.status == ActivityStatus.GEPLAND && prevStart != a.startAt) {
                        out += Notifier.Message(
                            a.id, "time", "Nieuwe tijd: ${a.title}",
                            "${whenText(a)} (aangepast door ${name(a.updatedBy)})",
                        )
                        reminded.remove(a.id)
                    }
                }
                // 4. Iemand doet mee met jouw activiteit
                if (prev != null && a.createdBy == me) {
                    val joined = a.participants.filter { it != me && it !in prevParticipants }
                    if (joined.isNotEmpty()) {
                        val count = if (a.maxParticipants > 0) "${a.participants.size}/${a.maxParticipants}"
                        else "${a.participants.size}"
                        out += Notifier.Message(
                            a.id, "joined", "${joined.joinToString(", ") { name(it) }} doet mee",
                            "${a.title} · $count deelnemers",
                        )
                    }
                }
            }

            // 5. Herinnering kort voor de start (ook bij de eerste controle)
            val untilStart = a.startAt - now
            if (a.status == ActivityStatus.GEPLAND && involved && a.id !in reminded &&
                untilStart in -5 * 60_000L..reminderWindowMs
            ) {
                out += Notifier.Message(a.id, "reminder", "Zo meteen: ${a.title}", withWhere("Om ${time(a.startAt)}", a))
                reminded += a.id
            }
        }

        // Nieuwe toestand bewaren
        val state = JSONObject()
        for (a in snap.activities) {
            state.put(a.id, JSONObject().apply {
                put("status", a.status.name)
                put("startAt", a.startAt)
                put("invited", a.invited.joinToString(","))
                put("participants", a.participants.joinToString(","))
            })
        }
        val ids = snap.activities.map { it.id }.toSet()
        prefs.edit()
            .putString(stateKey, state.toString())
            .putBoolean(baselineKey, true)
            .putStringSet(remindedKey, reminded.filter { it in ids }.toSet())
            .putLong("lastCheck_$eventId", now)
            .apply()

        out.forEach { Notifier.show(ctx, it) }
    }

    fun lastCheck(eventId: String): Long = prefs.getLong("lastCheck_$eventId", 0L)

    companion object {
        private val NL = Locale.forLanguageTag("nl-NL")
        private val zone get() = ZoneId.systemDefault()

        fun time(ms: Long): String =
            Instant.ofEpochMilli(ms).atZone(zone).format(DateTimeFormatter.ofPattern("HH:mm", NL))

        fun day(ms: Long): String {
            val d = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
            val today = LocalDate.now(zone)
            return when (d) {
                today -> "vandaag"
                today.plusDays(1) -> "morgen"
                else -> d.format(DateTimeFormatter.ofPattern("EEEE", NL))
            }
        }
    }
}
