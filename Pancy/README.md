# Pancy
App Android per seguire la gravidanza. Prima sezione: **appuntamenti**.

## Cosa fa (versione 1.0)
- Elenco degli appuntamenti **in programma** e **passati**, raggruppati per mese, con il prossimo in evidenza.
- Per ogni appuntamento: tipo (visita, ecografia, esami, corso, altro), titolo con suggerimenti pronti, data, ora (facoltativa), luogo, medico/ostetrica, note ed **esito**.
- **Settimana di gravidanza**: inserita la data presunta del parto (o l'ultima mestruazione), l'app mostra la settimana di oggi e quella di ogni appuntamento (es. 20+3).
- **Calendario del telefono** (facoltativo): copia gli appuntamenti in Google Calendar con il promemoria scelto; li aggiorna se li modifichi e li toglie se li elimini. Con un calendario condiviso li vede anche la compagna.
- Luogo apribile nell'app delle mappe; esito annotabile subito dal dettaglio.

## Come si compila
Come Mealprep: a ogni push su `main` GitHub Actions compila l'APK (scheda **Actions** → ultima esecuzione → **Artifacts** → `pancy-apk`).
La chiave di firma è fissa (`app/debug.keystore`), così gli aggiornamenti si installano sopra senza perdere i dati.

## Dove sono i dati
`appuntamenti.json` nella memoria dell'app (rientra nel backup automatico di Android). Formato:
```json
{ "versione": 1, "appuntamenti": [ { "id": "…", "tipo": "ecografia", "titolo": "Ecografia morfologica",
  "data": "2027-01-14", "ora": "09:30", "luogo": "…", "medico": "…", "note": "…", "esito": "…", "evento": 123 } ] }
```
Le impostazioni (data presunta del parto, calendario, promemoria) sono nelle preferenze `pancy`.

## Struttura del codice (`app/src/main/java/com/pancy/app`)
| File | Cosa contiene |
|---|---|
| `Appuntamento.kt` | modello, tipi, lettura/scrittura del file JSON |
| `Gravidanza.kt` | calcolo di settimana e trimestre dalla data presunta del parto |
| `CalendarSync.kt` | scrittura degli eventi nel calendario del telefono |
| `Model.kt` | stato dell'app e operazioni (salva, elimina, impostazioni) |
| `Lista.kt` | schermata principale |
| `Dettaglio.kt`, `Modifica.kt`, `Impostazioni.kt` | le altre schermate |
| `Componenti.kt`, `Tema.kt` | pezzi riutilizzabili, colori, formati di data |

## Prossime sezioni
Referti ed esiti, ecografie, foto e video, richieste mediche; poi l'assistente con l'IA che legge tutto lo storico.
