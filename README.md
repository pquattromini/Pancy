# Pancy
App Android per seguire la gravidanza. Versione 1.6: sei sezioni nella barra in basso, "Chiedi all'IA", calendario e Google Drive (anche per i PDF).

## Sezioni
| Sezione | Cosa fa |
|---|---|
| **Agenda** | Appuntamenti in programma e passati, con tipo, data, ora, luogo, medico, note ed esito. Copia facoltativa nel calendario del telefono con promemoria, e importazione facoltativa dal calendario (anche da quello condiviso dalla compagna). |
| **Ecografie** | Per ogni ecografia: misure del referto (peso stimato, battito, CRL, NT, BPD, HC, AC, FL), note, immagini e referto in PDF. Le ecografie in agenda già fatte compaiono come "da completare". |
| **Referti** | Esami del sangue e delle urine, test prenatali, tamponi, referti di visita, impegnative. Per ognuno: tipo, data, laboratorio o medico, esito in breve, note, PDF e foto del foglio. Gli esami in agenda già fatti compaiono come "da completare", e dall'appuntamento si aggiunge il referto. I PDF si leggono nell'app pagina per pagina (tocca per ingrandire) o si aprono con un'altra app. |
| **Foto** | Foto e video dalla galleria o dalla fotocamera, divisi per settimana di gravidanza. Schermo intero con zoom, video, didascalia, condivisione. |
| **Storico** | Peso della mamma (inserito a mano, con grafico e aumento dall'inizio) e peso stimato del bambino (preso dalle ecografie). |
| **Chiedi all'IA** | Pulsante "IA" in alto in ogni sezione. Chat con un modello OpenAI che riceve a ogni domanda tutti i dati dell'app (il "fascicolo") e può chiedere di vedere le foto che servono. Si possono allegare foto (per esempio un referto). Conversazioni salvate. |
| **Consigli** | Cosa fare in questo periodo, calendario degli esami gratuiti (LEA 2017) collegato all'agenda, vaccini, alimentazione, quando chiamare subito, fonti ISS. Più i consigli ricevuti da medico e ostetrica. |

La data presunta del parto (in Impostazioni) fa comparire ovunque la settimana di gravidanza (es. 20+3).

## Calendario del telefono
- **Copia** (app → calendario): gli appuntamenti creati nell'app diventano eventi, con promemoria. Gli eventi creati dall'app hanno in fondo alla descrizione "(dall'app Pancy)".
- **Importazione** (calendario → app): a ogni apertura dell'app si leggono i calendari scelti, dall'inizio della gravidanza a un anno da oggi.
  - Facoltativo: solo gli eventi che contengono certe parole (predefinite in `PAROLE_PREDEFINITE`, `Model.kt`).
  - Un evento importato è collegato con `esterno` (calendario:evento) e `firma` (titolo, data, ora, luogo): se nel calendario cambia, si aggiornano titolo, data, ora e luogo; tipo, medico, note ed esito restano quelli dell'app.
  - Evento cancellato nel calendario: l'appuntamento sparisce se non è ancora passato e non ha esito, altrimenti resta scollegato.
  - Eliminato nell'app: finisce tra gli ignorati e non torna più.
  - Gli eventi creati dall'app non si importano, e gli appuntamenti importati non si ricopiano.

## Google Drive
Foto, video, immagini e PDF si sincronizzano nei due sensi con tre cartelle di Drive: Foto, Ecografie e Referti (le ultime due facoltative).
- **Foto**: solo foto e video.
- **Ecografie**: immagini e PDF, collegati all'ecografia dello stesso giorno.
- **Referti**: PDF e foto. Ogni PDF nuovo diventa un referto (titolo e tipo presi dal nome del file, per esempio `2026-10-03 esami sangue.pdf` → "Esami sangue", tipo Esami del sangue); le foto nuove dello stesso giorno diventano un referto solo. Se in agenda quel giorno c'è un appuntamento di tipo Esami, il referto si collega lì.
- Senza la cartella dei referti, i file dei referti restano solo sul telefono (lo stesso per i PDF delle ecografie senza la cartella delle ecografie).

### Configurazione (una volta sola, meglio dal computer)
1. Apri [console.cloud.google.com](https://console.cloud.google.com) con lo stesso account Google delle cartelle. In alto: **Seleziona un progetto → Nuovo progetto**, nome `Pancy`, **Crea**.
2. **API e servizi → Libreria**: cerca **Google Drive API** e tocca **Abilita**.
3. **Google Auth Platform** (in alcune versioni si chiama "Schermata consenso OAuth") → **Inizia**:
   - nome app `Pancy` e la tua email;
   - pubblico **Esterno**;
   - la tua email come contatto;
   - poi **Crea**.
4. **Pubblico → Utenti di test → + Add users**: aggiungi il tuo indirizzo Gmail (lo stesso delle cartelle) e salva. L'app resta "in test": non serve pubblicarla, e spesso Google non lo permette finché non si compila tutta la pagina Branding (home page, privacy policy, domini).
   - In test Google chiede di confermare l'accesso circa ogni 7 giorni. Quando succede, in Foto ed Ecografie compare un avviso: tocca **Ricollega** e conferma.
   - Ogni account che deve usare Drive dall'app (per esempio quello della compagna, se lo usa sul suo telefono) va aggiunto agli utenti di test.
5. **Client → Crea client**:
   - tipo **Android**;
   - nome pacchetto `com.pancy.app`;
   - impronta SHA-1 `C0:9A:D4:70:0E:6E:AE:32:16:FC:9E:00:A0:DD:FB:D3:3C:A9:41:5B`;
   - poi **Crea**. L'impronta è quella della chiave `app/debug.keystore` con cui GitHub firma l'APK.
6. Nell'app: **Impostazioni → Google Drive → Collega**. Google avvisa che l'app non è verificata: tocca **Avanzate → Vai a Pancy** e consenti. È normale per un'app personale non pubblicata sul Play Store.
7. Scegli la cartella delle foto e, se vuoi, quella delle ecografie e quella dei referti (tutte diverse fra loro).

### Come funziona
- Autorizzazione con `AuthorizationClient` di Google Play services e ambito `https://www.googleapis.com/auth/drive`. Le chiamate all'API REST v3 di Drive sono in `Drive.kt`.
- Se Google chiede di ridare l'accesso (`serveConsenso` o errore 401), `Model.driveServeConsenso` diventa vero e `AvvisoDrive` (in `DriveUI.kt`) mostra il pulsante «Ricollega» in Foto ed Ecografie. Per gli altri problemi l'avviso porta alle impostazioni.
- Sincronizzazione (`SincroniaDrive`) all'apertura dell'app e quando si aggiunge, modifica o elimina un file:
  - foto aggiunte nell'app: caricate su Drive, oppure collegate a un file uguale se c'è già (stesso MD5);
  - file nuovi su Drive: entrano nell'app e si scaricano solo quando servono (prima l'anteprima di Drive, poi il file intero);
  - eliminate nell'app: finiscono nel cestino di Drive;
  - eliminate su Drive: tolte dall'app;
  - spostate su Drive da una cartella all'altra: vince Drive;
  - cambiate nell'app (per esempio un'ecografia eliminata): l'app sposta il file su Drive.
- Su Drive l'app salva la didascalia nella descrizione del file e, come proprietà private, la data (`pancyData`), il giorno dell'ecografia (`pancyEcografia`) e, per i referti, id, tipo e titolo (`pancyReferto`, `pancyTipo`, `pancyTitolo`, al massimo 124 byte ciascuna). Così, reinstallando l'app, tutto torna al suo posto. Esito e note dei referti tornano con il backup di Android.
- Un file mancante su Drive si toglie dall'app solo se la sua cartella è stata letta in quella sincronizzazione.
- Le immagini della cartella Ecografie si collegano all'ecografia dello stesso giorno, che l'app crea se manca (con il titolo dell'appuntamento in agenda, se c'è).

## Chiedi all'IA (OpenAI)
- Serve una chiave API di OpenAI, da inserire in Impostazioni → Chiedi all'IA. **Mai nel codice**: il repository è pubblico.
- La chiave sta nelle preferenze `segreti`, escluse dal backup.
- Chiamata: API Responses (`/v1/responses`) con `store: false`. Le istruzioni contengono le regole e il fascicolo con tutti i dati in testo (`Fascicolo` in `Assistente.kt`).
- Il fascicolo contiene anche i referti (tipo, esito, note, appuntamento) con l'elenco dei loro file.
- Strumento `guarda_file`: l'IA chiede i file per codice. Le immagini (`F-xxxxxx`) arrivano ridotte a 1600 px; i PDF (`D-xxxxxx`) arrivano interi come `input_file` (base64), così OpenAI ne legge testo e pagine. Limiti dell'app: 10 MB per PDF, 25 MB di PDF per risposta (OpenAI accetta fino a 50 MB per richiesta).
- Modello predefinito `gpt-6.1-terra`, modificabile nelle impostazioni (elenco in `MODELLI_IA`).

## Come si compila
A ogni push su `main` GitHub Actions compila l'APK (scheda **Actions** → ultima esecuzione → **Artifacts** → `pancy-apk`).
La chiave di firma è fissa (`app/debug.keystore`), così gli aggiornamenti si installano sopra senza perdere i dati.

## Dove sono i dati
Nella memoria dell'app, un file JSON per sezione: `appuntamenti.json`, `ecografie.json`, `referti.json`, `media.json`, `pesi.json`, `consigli.json`, `conversazioni.json`.
Formato comune: `{ "versione": 1, "<sezione>": [ ... ] }`. Foto, video e PDF sono nella cartella `media/` (in `media.json` i PDF hanno `"pdf": true`, i file dei referti `"referto"`), le foto allegate alla chat in `chat/`.
Le impostazioni (data presunta del parto, peso prima della gravidanza, calendario, promemoria) sono nelle preferenze `pancy`.
Il backup automatico di Android include tutto tranne `media/`, `chat/` e `miniature/` (troppo grandi per il limite di 25 MB) e la chiave API. Con Google Drive collegato, foto e video tornano comunque da Drive.

## Struttura del codice (`app/src/main/java/com/pancy/app`)
| File | Cosa contiene |
|---|---|
| `Navigazione.kt` | sezioni, schermate, barra in basso e in alto |
| `Model.kt` | stato dell'app e tutte le operazioni |
| `Archivio.kt` | lettura e scrittura dei file JSON |
| `Appuntamento.kt`, `Dati.kt` | modelli: appuntamento, ecografia e misure, referto, foto/video/PDF, peso, consiglio; titoli e date dai nomi dei file |
| `Gravidanza.kt` | settimana e trimestre dalla data presunta del parto |
| `CalendarSync.kt` | eventi nel calendario del telefono |
| `Galleria.kt` | copia, miniature, condivisione e apertura di foto, video e PDF |
| `Pdf.kt` | pagine dei PDF come immagini (`PdfRenderer` di Android) |
| `Drive.kt` | API di Google Drive e sincronizzazione nei due sensi |
| `DriveUI.kt` | sezione Google Drive delle impostazioni e scelta delle cartelle |
| `Guida.kt` | contenuti fissi dei Consigli (esami, vaccini, alimentazione, fonti) |
| `Grafico.kt` | grafico a linea usato nello Storico |
| `Assistente.kt` | conversazioni, chiamata a OpenAI, fascicolo dei dati per l'IA |
| `ChiediIA.kt` | schermate della chat e sezione IA delle impostazioni |
| `Lista.kt`, `Dettaglio.kt`, `Modifica.kt` | Agenda |
| `Referti.kt` | sezione Referti: elenco, dettaglio, modifica |
| `Documento.kt` | visore dei PDF |
| `Ecografie.kt`, `Foto.kt`, `Visore.kt`, `Storico.kt`, `Consigli.kt`, `Impostazioni.kt` | le altre schermate |
| `Componenti.kt`, `Tema.kt` | pezzi riutilizzabili, colori, formati di date e numeri |

## Prossimi passi
- Allegare un PDF direttamente a una domanda nella chat dell'IA (ora si passa dalla sezione Referti).
- Valori degli esami in forma strutturata (emoglobina, glicemia…) con grafico nello Storico.
