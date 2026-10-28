# Spellenweekend

Android-app (Kotlin + Jetpack Compose) voor een spellenweekend met meerdere deelnemers.

## Wat de app doet

| Tabblad | Inhoud |
|---|---|
| **Nu** | Je eigen status (Beschikbaar / Reserveerbaar / Bezet + toelichting), uitnodigingen, wat er nu loopt, wat er straks begint, wie er is |
| **Planning** | Alle activiteiten per dag: spellen, eten, wandelen, overig. Uitnodigen, meedoen, starten, afronden, annuleren |
| **Spellen** | Gezamenlijke spellenlijst (spelers, duur, zwaarte, wie het meeneemt). Met ♥ geef je aan wat je wilt spelen |
| **Match** | Suggesties: welk spel kan nú met wie, op welke vrije tafel. Plus "bijna"-spellen waar nog 1–2 spelers voor nodig zijn |
| **Meer** | Weekend (adres, route, in-/uitchecken, afspraken, meenemen), Kamers, Corvee, Beheer (code, locaties, tafels, deelnemers, import) |

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

In de app zit een importbestand (`app/src/main/assets/seed_medael_2026.json`), gemaakt uit
*Slimbo weekend – Wie neemt wat mee* met `tools/import_slimbo.py`. Eén tik op **Laden**
(op het tabblad Nu of onder Meer → Beheer) zet alles in het weekend:

| Onderdeel | Inhoud |
|---|---|
| Locaties | Residentie 17 en Residentie 18, Rue de Rémersdael 98, 4852 Hombourg (B) |
| Tafels per residentie | Eettafel A (11), Eettafel B (11), loungehoek (4), 2 picknicktafels op het terras (6) |
| Kamers | 16 kamers: per residentie op de 1e en 2e verdieping één 2-pers. en drie 3-pers. kamers, met de kamerindeling |
| Gastenlijst | 42 namen uit de kamerindeling; iedereen koppelt zichzelf in de app aan zijn naam |
| Spellen | 105 titels: wie het meeneemt of *Wens*, BGG-link, en wie interesse heeft (komt bij koppelen op je ♥-lijst) |
| Corvee | Rooster per residentie, vrijdagavond t/m maandagochtend |
| Weekendinfo | In-/uitchecken, zelf meenemen, gezamenlijke inkopen, afspraken, carpool, te koop aangeboden, Murder Mystery |
| Planning | Inchecken vrijdag 16:00 en uitchecken maandag (uiterlijk 10:00) |

Aannames bij de import:
- **Data:** komend weekend = vrijdag 6 t/m maandag 9 november 2026.
- **Tafels:** de site noemt per residentie 2 grote tafels met samen 22 stoelen en 2 picknicktafels op het terras. Zitplaatsen voor loungehoek en picknicktafels zijn geschat; pas ze aan onder Meer → Beheer.
- **Kamernummers** (1.1 t/m 2.4) zijn toegekend in de volgorde van de kamerindeling; het document heeft geen nummers.
- **Bedden:** eenpersoons boxsprings 90×200 (volgens de website), dus hoeslaken 90×200.
- **Spelersaantal/duur:** het document bevat die niet. Voor 41 bekende titels zijn ze ingevuld; de overige 64 tonen "⚠ nog invullen" met de standaard 2–4 spelers / 60 min. Openen, controleren via de BGG-link en opslaan haalt de waarschuwing weg.
- **Wenslijst-spellen** (eigenaar "Wens") doen niet mee in Match, omdat niemand ze meeneemt.
- **Namen gelijkgetrokken:** Melisss → Melissa, Melanie → Mélanie, Dennis L → Dennis, "Vriendin" → Vriendin van Wesley.
- **Niet overgenomen:** achternamen, adressen, postcodes, nationaliteit en geboortedata (Gastenfiche en Verjaardagen). Die horen niet in een gedeelde app.

Nieuwe versie van het document? `python3 tools/import_slimbo.py <bestand.xlsx>`, committen, en daarna in de app opnieuw **Laden**.

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
tools/              import_slimbo.py: Excel → importbestand
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
