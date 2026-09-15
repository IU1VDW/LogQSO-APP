# Log QSO — consultazione log ADIF per Android

App di sola lettura per consultare il log da telefono. Non si scrive nulla: si importa
un file ADIF (inoltrandolo da Telegram, dalla mail o da un gestore file) e poi si cerca,
si ordina e si filtra.

Nominativo: IU1VDW.

## Cosa fa

- **Import per condivisione**: da Telegram (o da qualsiasi app) → *Condividi* → *Log QSO*.
  Funziona anche toccando un file `.adi` in un gestore file, o con *Importa file ADIF…*
  dal menu dell'app. Al secondo import chiede se **sostituire** o **unire** (l'unione
  scarta i doppioni: stesso call + data + ora + banda + modo).
- **Ordinamento** su tutte le colonne richieste: DATA/ORA, BANDA, MODO, CALL, NOTE,
  LOTW, QSL SPED, QSL RIC — con inversione dell'ordine. La banda si ordina in ordine
  radiantistico (160 m → 10 m → 70 cm), non alfabetico.
- **Ricerca libera** su tutti i campi del QSO: nominativo, note, locatore, paese, nome…
  Più parole = tutte devono comparire.
- **Filtri rapidi**: banda, modo, anno, paese (a scelta multipla) e conferme
  (solo confermati LoTW, solo non confermati, solo con QSL spedita/ricevuta, senza conferme).
- **Dettaglio a schermo intero**: toccando una riga si vedono tutti i campi ADIF del QSO,
  prima quelli utili tradotti in italiano, poi tutto il resto. Testo selezionabile.
- Il log resta **in memoria interna dell'app**, offline: nessuna rete, nessun permesso.

## Campi letti dall'ADIF

| Colonna   | Campo ADIF |
|-----------|------------|
| DATA      | `QSO_DATE` (mostrata gg/mm/aaaa) |
| ORA       | `TIME_ON` (in mancanza, `TIME_OFF`) |
| BANDA     | `BAND` |
| MODO      | `SUBMODE` se presente (FT4, JS8…), altrimenti `MODE` |
| CALL      | `CALL` |
| NOTE      | `COMMENT` + `NOTES` + `QSLMSG` |
| LOTW      | `LOTW_QSL_RCVD` (Y/V = confermato) |
| QSL SPED  | `QSL_SENT` (Y/Q = spedita o in coda) |
| QSL RIC   | `QSL_RCVD` (Y/V = ricevuta) |

Il parser è tollerante: legge gli export di Log4OM, QRZ, Club Log, LoTW e WSJT-X,
con o senza intestazione, e ignora i campi che non conosce (conservandoli comunque
per la schermata di dettaglio).

## Come ottenere l'APK

### 1) Via GitHub Actions (nessun software da installare)

1. Crea un repository su GitHub (anche privato) e caricaci questa cartella
   (drag & drop dei file su *Add file → Upload files* va benissimo).
2. Il workflow `.github/workflows/build.yml` parte da solo a ogni push.
   Vai su **Actions → Compila APK → Artifacts** e scarica `LogQSO-apk`.
3. Se preferisci un link diretto scaricabile dal telefono:
   `git tag v1.0 && git push --tags` → l'APK finisce nelle **Releases**.

### 2) Con Android Studio

Apri la cartella, lascia sincronizzare Gradle e usa *Build → Build APK(s)*.
L'APK esce in `app/build/outputs/apk/debug/`.

### 3) Da riga di comando

Con Android SDK e Gradle 8.9+ installati:

```
gradle assembleDebug
```

Il progetto non include il Gradle wrapper: Android Studio lo genera al primo
sync, e la build su GitHub Actions usa Gradle installato dall'action.

## Installazione sul telefono

L'APK è firmato con la chiave di debug, quindi Android chiederà di autorizzare
l'installazione da **origini sconosciute** per l'app da cui apri il file
(browser o gestore file). È l'unico passaggio manuale.

## Struttura

```
app/src/main/java/net/iu1vdw/qsolog/
  AdifParser.kt     lettura del file ADIF
  Qso.kt            il singolo collegamento e le sue colonne
  QsoQuery.kt       ordinamento, filtri, ricerca, unione senza doppioni
  LogStore.kt       archivio JSON in memoria interna
  QsoAdapter.kt     righe della lista
  MainActivity.kt   schermata principale
  DetailActivity.kt scheda del QSO
```

La logica di parsing, ordinamento e filtro (`AdifParser`, `Qso`, `QsoQuery`) è
codice Kotlin puro, senza dipendenze da Android: è stata compilata ed eseguita
contro un ADIF di prova prima della consegna.

## Prova della logica senza Android

`tools/AdifSelfTest.kt` è il test usato in fase di sviluppo: compila
`AdifParser.kt`, `Qso.kt`, `QsoQuery.kt` su JVM e verifica parsing, ordinamenti,
filtri, ricerca e unione su un ADIF di esempio. Con il compilatore Kotlin:

```
kotlinc app/src/main/java/net/iu1vdw/qsolog/{Qso,AdifParser,QsoQuery}.kt tools/AdifSelfTest.kt -include-runtime -d test.jar
java -jar test.jar
```
