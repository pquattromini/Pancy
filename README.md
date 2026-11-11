# Pancy
App Android per seguire la gravidanza. Versione 1.2: cinque sezioni nella barra in basso più "Chiedi all'IA".

## Sezioni
| Sezione | Cosa fa |
|---|---|
| **Agenda** | Appuntamenti in programma e passati, con tipo, data, ora, luogo, medico, note ed esito. Copia facoltativa nel calendario del telefono con promemoria. |
| **Ecografie** | Per ogni ecografia: misure del referto (peso stimato, battito, CRL, NT, BPD, HC, AC, FL), note e immagini. Le ecografie in agenda già fatte compaiono come "da completare". |
| **Foto** | Foto e video dalla galleria o dalla fotocamera, divisi per settimana di gravidanza. Schermo intero con zoom, video, didascalia, condivisione. |
| **Storico** | Peso della mamma (inserito a mano, con grafico e aumento dall'inizio) e peso stimato del bambino (preso dalle ecografie). |
| **Chiedi all'IA** | Pulsante "IA" in alto in ogni sezione. Chat con un modello OpenAI che riceve a ogni domanda tutti i dati dell'app (il "fascicolo") e può chiedere di vedere le foto che servono. Si possono allegare foto (per esempio un referto). Conversazioni salvate. |
| **Consigli** | Cosa fare in questo periodo, calendario degli esami gratuiti (LEA 2017) collegato all'agenda, vaccini, alimentazione, quando chiamare subito, fonti ISS. Più i consigli ricevuti da medico e ostetrica. |

La data presunta del parto (in Impostazioni) fa comparire ovunque la settimana di gravidanza (es. 20+3).

## Chiedi all'IA (OpenAI)
- Serve una chiave API di OpenAI, da inserire in Impostazioni → Chiedi all'IA. **Mai nel codice**: il repository è pubblico.
- La chiave sta nelle preferenze `segreti`, escluse dal backup.
- Chiamata: API Responses (`/v1/responses`) con `store: false`. Le istruzioni contengono le regole e il fascicolo con tutti i dati in testo (`Fascicolo` in `Assistente.kt`).
- Strumento `guarda_immagini`: l'IA chiede le foto per codice (`F-xxxxxx`), l'app le manda ridotte a 1600 px.
- Modello predefinito `gpt-6.1-terra`, modificabile nelle impostazioni (elenco in `MODELLI_IA`).

## Come si compila
A ogni push su `main` GitHub Actions compila l'APK (scheda **Actions** → ultima esecuzione → **Artifacts** → `pancy-apk`).
La chiave di firma è fissa (`app/debug.keystore`), così gli aggiornamenti si installano sopra senza perdere i dati.

## Dove sono i dati
Nella memoria dell'app, un file JSON per sezione: `appuntamenti.json`, `ecografie.json`, `media.json`, `pesi.json`, `consigli.json`, `conversazioni.json`.
Formato comune: `{ "versione": 1, "<sezione>": [ ... ] }`. Foto e video sono nella cartella `media/`, le foto allegate alla chat in `chat/`.
Le impostazioni (data presunta del parto, peso prima della gravidanza, calendario, promemoria) sono nelle preferenze `pancy`.
Il backup automatico di Android include tutto tranne `media/` e `chat/` (troppo grandi per il limite di 25 MB) e la chiave API.

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
| `Assistente.kt` | conversazioni, chiamata a OpenAI, fascicolo dei dati per l'IA |
| `ChiediIA.kt` | schermate della chat e sezione IA delle impostazioni |
| `Lista.kt`, `Dettaglio.kt`, `Modifica.kt` | Agenda |
| `Ecografie.kt`, `Foto.kt`, `Visore.kt`, `Storico.kt`, `Consigli.kt`, `Impostazioni.kt` | le altre schermate |
| `Componenti.kt`, `Tema.kt` | pezzi riutilizzabili, colori, formati di date e numeri |

## Prossimi passi
Referti e richieste mediche (foto o PDF collegati agli appuntamenti), da far leggere anche all'IA.
