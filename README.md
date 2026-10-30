# Spellenweekend

## 🔗 Links

| Wat | Voor wie | Link |
|---|---|---|
| 📱 **Android-app** (APK, altijd de nieuwste) | deelnemers met Android | https://github.com/mheunen/Slimbo_weekend/releases/latest/download/Spellenweekend.apk |
| 🌐 **Web-app** (iPhone & browser) | deelnemers met iPhone, of zonder APK | https://mheunen.github.io/Slimbo_weekend/app/ |
| 🛠️ **Beheersite** | organisatie | https://mheunen.github.io/Slimbo_weekend/ |
| 📝 Release-notes per versie | iedereen | https://github.com/mheunen/Slimbo_weekend/releases |

Android-app (Kotlin + Jetpack Compose) voor een spellenweekend met meerdere deelnemers.

## Wat de app doet

| Tabblad | Inhoud |
|---|---|
| **Nu** | Je eigen status (Beschikbaar / Reserveerbaar / Bezet + toelichting), uitnodigingen, wat er nu loopt, wat er straks begint, wie er is |
| **Planning** | Alle activiteiten per dag: spellen, eten, wandelen, overig. Uitnodigen, meedoen, starten, afronden, annuleren |
| **Spellen** | Gezamenlijke spellenlijst (spelers, duur, zwaarte, wie het meeneemt). Met ♥ geef je aan wat je wilt spelen |
| **Match** | Suggesties: welk spel kan nú met wie, op welke vrije tafel. Plus "bijna"-spellen waar nog 1–2 spelers voor nodig zijn |
| **Meer** | Weekend (adres, route, in-/uitchecken, afspraken, meenemen), Kamers, Corvee, Beheer (code, locaties, tafels, deelnemers — inklapbaar) |

**Volledig gratis:** draait op het Firebase **Spark**-abonnement (geen creditcard, geen Blaze).

**Vernieuwen-knop** (↻ rechtsboven): haalt alles opnieuw van de server en toont wanneer het laatst is bijgewerkt.
Terwijl de app open is komen wijzigingen ook vanzelf live binnen.

**Meldingen** maakt de telefoon zelf (geen server nodig):
- uitnodiging voor een activiteit
- een spel/activiteit start ("Wingspan begint nu!")
- herinnering ±5–20 minuten voor de start
- tijd gewijzigd of geannuleerd
- iemand doet mee met jouw activiteit (voor de organisator)

Werkt de app op de achtergrond, dan controleert Android ongeveer elke 15 minuten (het minimum dat Android
toestaat; bij batterijbesparing soms later). Meldingen kunnen dus tot een kwartier vertraagd zijn.
Tip voor het weekend: zet batterijoptimalisatie voor Spellenweekend uit
(*Instellingen → Apps → Spellenweekend → Batterij → Onbeperkt*).

**Automatisch:** bij *Start* gaan alle deelnemers op *Bezet*, bij *Klaar* weer op *Beschikbaar*.

## Slimbo-weekend · Molen van Medael (6 – 9 november 2026)

De database is één keer gevuld vanuit het Slimbo-document (*Wie neemt wat mee*): locaties, kamers, gastenlijst, corvee, weekendinfo en de spellenlijst.
**Het document wordt daarna nergens meer voor gebruikt.** Alle wijzigingen gebeuren in de app, de web-app of op de beheersite, en de database is de enige bron.

- Er is geen knop *Laden* meer: niet in de app en niet op de beheersite.
- Interesse in spellen = alleen de ♥'s die iemand zelf in de app zet. Bij het koppelen aan je naam komen er geen ♥'s uit het document bij; alleen de ♥'s van je eigen oude toestel gaan mee.
- Het importscript, het importbestand en de BGG-tooling daarvoor zijn verwijderd (staan nog in de git-geschiedenis).

## Gemaakte keuzes (aannames)

- **Inloggen:** anoniem via Firebase + naam. Geen wachtwoorden; meedoen met een 6-tekencode. Nadeel: na herinstallatie ben je een nieuwe deelnemer.
- **Iedereen in het weekend mag alles bewerken** (spellen, tafels, activiteiten). Voor een vriendengroep is dat het handigst; de Firestore-regels houden buitenstaanders buiten.
- **Matchmaking:** kandidaten = wie het spel met ♥ heeft gemarkeerd en niet op Bezet staat of in een lopend spel zit. "Beschikbaar" gaat voor "Reserveerbaar". Een tafel telt als bezet als er iets loopt of binnen het komende uur gepland staat.
- **Debug-build met vaste sleutel** (`app/debug.keystore`). Daardoor installeert elke nieuwe APK over de vorige heen. Niet bedoeld voor de Play Store.

## Eenmalige installatie

### 1. Firebase-project (gratis Spark-abonnement)
1. Ga naar <https://console.firebase.google.com> → **Project toevoegen** (Analytics mag uit).
2. **Build → Authentication → Aan de slag → Aanmeldmethode → Anoniem → inschakelen.**
3. **Build → Firestore Database → Database maken** → locatie **`europe-west1` (België)** → productiemodus.
4. In Firestore → tabblad **Regels**: vervang alles door de inhoud van [`firestore.rules`](firestore.rules) → **Publiceren**.

Het project blijft op **Spark**. Upgraden naar Blaze is niet nodig.

### 2. Android-app registreren
1. Projectoverzicht → **Android-app toevoegen**.
2. Pakketnaam: **`nl.spellenweekend.app`**.
3. Download **`google-services.json`**. (Niet in de repo zetten — die gaat in een secret.)

### 3. Secret in GitHub
Repo → **Settings → Secrets and variables → Actions → New repository secret**:

| Naam | Waarde |
|---|---|
| `GOOGLE_SERVICES_JSON` | volledige inhoud van `google-services.json` |

### 4. APK bouwen
Repo → tabblad **Actions → Android APK → Run workflow**. Daarna bouwt GitHub automatisch bij elke push naar `main`.

### 5. Installeren op je telefoon
Open op je telefoon **Releases** van de repo (of
`https://github.com/<gebruiker>/<repo>/releases/latest/download/Spellenweekend.apk`),
download `Spellenweekend.apk` en sta "installeren uit onbekende bronnen" toe voor je browser.
Bij een privé-repo moet je ingelogd zijn op GitHub, of je deelt de APK met de groep via bv. WhatsApp.

## Projectstructuur

```
app/src/main/java/nl/spellenweekend/app/
  data/       Models, Repository (Firestore), Matchmaker
  ui/         schermen (Nu, Planning, Spellen, Match, Meer), editor, thema
  notify/     eigen meldingen: vergelijken met vorige stand (ChangeChecker),
              achtergrondcontrole elke ~15 min (CheckWorker), tonen (Notifier)
firestore.rules     beveiligingsregels (handmatig plakken in de console)
.github/workflows/  android.yml (APK bouwen)
```

## Datamodel (Firestore)

```
events/{eventId}                 name, joinCode, createdBy
  members/{uid}                  name, status, statusNote, wants[], guestId
  games/{id}                     name, minPlayers, maxPlayers, durationMin, weight, owner, notes
  places/{id}                    name, description
  tables/{id}                    name, placeId, seats
  activities/{id}                type, title, gameId, placeId, tableId, startAt, durationMin,
                                 maxParticipants, createdBy, participants[], invited[], declined[],
                                 status, notes, updatedBy
```
