# Pancy
App Android per seguire la gravidanza. Versione 1.1: cinque sezioni nella barra in basso.

## Sezioni
| Sezione | Cosa fa |
|---|---|
| **Agenda** | Appuntamenti in programma e passati, con tipo, data, ora, luogo, medico, note ed esito. Copia facoltativa nel calendario del telefono con promemoria. |
| **Ecografie** | Per ogni ecografia: misure del referto (peso stimato, battito, CRL, NT, BPD, HC, AC, FL), note e immagini. Le ecografie in agenda già fatte compaiono come "da completare". |
| **Foto** | Foto e video dalla galleria o dalla fotocamera, divisi per settimana di gravidanza. Schermo intero con zoom, video, didascalia, condivisione. |
| **Storico** | Peso della mamma (inserito a mano, con grafico e aumento dall'inizio) e peso stimato del bambino (preso dalle ecografie). |
| **Consigli** | Cosa fare in questo periodo, calendario degli esami gratuiti (LEA 2017) collegato all'agenda, vaccini, alimentazione, quando chiamare subito, fonti ISS. Più i consigli ricevuti da medico e ostetrica. |

La data presunta del parto (in Impostazioni) fa comparire ovunque la settimana di gravidanza (es. 20+3).

## Come si compila
A ogni push su `main` GitHub Actions compila l'APK (scheda **Actions** → ultima esecuzione → **Artifacts** → `pancy-apk`).
La chiave di firma è fissa (`app/debug.keystore`), così gli aggiornamenti si installano sopra senza perdere i dati.

## Dove sono i dati
Nella memoria dell'app, un file JSON per sezione: `appuntamenti.json`, `ecografie.json`, `media.json`, `pesi.json`, `consigli.json`.
Formato comune: `{ "versione": 1, "<sezione>": [ ... ] }`. Foto e video sono nella cartella `media/`.
Le impostazioni (data presunta del parto, peso prima della gravidanza, calendario, promemoria) sono nelle preferenze `pancy`.
Il backup automatico di Android include tutto tranne `media/` (troppo grande per il limite di 25 MB).

## Struttura del codice (`app/src/main/java/com/pancy/app`)
| File | Cosa contiene |
|---|---|
| `Navigazione.kt` | sezioni, schermate, barra in basso e in alto |
| `Model.kt` | stato dell'app e tutte le operazioni |
| `Archivio.kt` | lettura e scrittura dei file JSON |
| `Appuntamento.kt`, `Dati.kt` | modelli: appuntamento, ecografia e misure, foto/video, peso, consiglio |
| `Gravidanza.kt` | settimana e trimestre dalla data presunta del parto |
| `CalendarSync.kt` | eventi nel calendario del telefono |
| `Galleria.kt` | copia, miniature e condivisione di foto e video |
| `Guida.kt` | contenuti fissi dei Consigli (esami, vaccini, alimentazione, fonti) |
| `Grafico.kt` | grafico a linea usato nello Storico |
| `Lista.kt`, `Dettaglio.kt`, `Modifica.kt` | Agenda |
| `Ecografie.kt`, `Foto.kt`, `Visore.kt`, `Storico.kt`, `Consigli.kt`, `Impostazioni.kt` | le altre schermate |
| `Componenti.kt`, `Tema.kt` | pezzi riutilizzabili, colori, formati di date e numeri |

## Prossimi passi
Referti e richieste mediche (foto o PDF collegati agli appuntamenti); poi l'assistente con l'IA che legge tutti i file JSON e i documenti.
