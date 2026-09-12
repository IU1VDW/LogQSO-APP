import net.iu1vdw.qsolog.*

fun main() {
    val adif = """
Log4OM ADIF export
<ADIF_VER:5>3.1.4
<PROGRAMID:7>Log4OM2
<EOH>

<CALL:6>DL1ABC<QSO_DATE:8>20260115<TIME_ON:6>203015<BAND:3>20m<MODE:4>MFSK<SUBMODE:3>FT4<FREQ:9>14.080000<RST_SENT:3>-07<RST_RCVD:3>-12<GRIDSQUARE:4>JO31<COUNTRY:7>Germany<COMMENT:16>Bel collegamento<LOTW_QSL_RCVD:1>Y<QSL_SENT:1>N<QSL_RCVD:1>N<EOR>
<CALL:5>K1XYZ<QSO_DATE:8>20251002<TIME_ON:4>2312<BAND:3>40m<MODE:3>FT8<COUNTRY:24>United States of America<LOTW_QSL_RCVD:1>N<QSL_SENT:1>Y<QSL_RCVD:1>Y<EOR>
<call:6>JA1ZZZ<qso_date:8>20260720<time_on:6>121500<band:3>15m<mode:2>CW<rst_sent:3>599<lotw_qsl_rcvd:1>V<qsl_sent:1>Q<qsl_rcvd:1>N<notes:9>Prima JA!<EOR>
<CALL:6>EA3ABC<QSO_DATE:8>20260301<TIME_ON:6>190000<BAND:4>160m<MODE:3>SSB<APP_LOG4OM_X:3:S>abc<EOR>
""".trimIndent()

    val qsos = AdifParser.parse(adif)
    check(qsos.size == 4) { "attesi 4 QSO, trovati ${qsos.size}" }

    val q = qsos[0]
    check(q.call == "DL1ABC")
    check(q.dateDisplay == "15/01/2026") { q.dateDisplay }
    check(q.timeDisplay == "20:30") { q.timeDisplay }
    check(q.mode == "FT4") { q.mode }
    check(q.band == "20M")
    check(q.note == "Bel collegamento") { "nota='${q.note}'" }
    check(q.lotwConfirmed)
    check(!q.qslRcvdDone)

    val ja = qsos[2]
    check(ja.call == "JA1ZZZ") { ja.call }
    check(ja.timeDisplay == "12:15") { ja.timeDisplay }
    check(ja.lotwConfirmed) { "V deve valere confermato" }
    check(ja.qslSentDone) { "Q deve valere spedita/in coda" }
    check(ja.note.startsWith("Prima JA")) { "nota='${ja.note}'" }

    val k1 = qsos[1]
    check(k1.timeRaw == "231200") { k1.timeRaw }

    // ordinamenti
    val byDateDesc = QsoQuery.apply(qsos, Filters())
    check(byDateDesc.first().call == "JA1ZZZ") { "piu' recente: ${byDateDesc.first().call}" }
    check(byDateDesc.last().call == "K1XYZ") { "piu' vecchio: ${byDateDesc.last().call}" }

    val byBand = QsoQuery.apply(qsos, Filters(sort = SortField.BAND, reversed = false))
    check(byBand.map { it.band } == listOf("160M", "40M", "20M", "15M")) { byBand.map { it.band }.toString() }

    val byCall = QsoQuery.apply(qsos, Filters(sort = SortField.CALL, reversed = false))
    check(byCall.map { it.call } == listOf("DL1ABC", "EA3ABC", "JA1ZZZ", "K1XYZ")) { byCall.map { it.call }.toString() }

    // filtri
    check(QsoQuery.apply(qsos, Filters(bands = setOf("20M"))).size == 1)
    check(QsoQuery.apply(qsos, Filters(modes = setOf("FT8"))).single().call == "K1XYZ")
    check(QsoQuery.apply(qsos, Filters(years = setOf("2026"))).size == 3)
    check(QsoQuery.apply(qsos, Filters(conf = ConfFilter.LOTW_OK)).size == 2)
    check(QsoQuery.apply(qsos, Filters(conf = ConfFilter.QSL_MISSING)).map { it.call } == listOf("EA3ABC")) {
        QsoQuery.apply(qsos, Filters(conf = ConfFilter.QSL_MISSING)).map { it.call }.toString()
    }

    // ricerca libera (su tutti i campi, anche paese e locatore)
    check(QsoQuery.apply(qsos, Filters(text = "germany")).single().call == "DL1ABC")
    check(QsoQuery.apply(qsos, Filters(text = "jo31")).size == 1)
    check(QsoQuery.apply(qsos, Filters(text = "ja1")).size == 1)
    check(QsoQuery.apply(qsos, Filters(text = "united states")).size == 1)
    check(QsoQuery.apply(qsos, Filters(text = "15/01/2026")).size == 1)
    check(QsoQuery.apply(qsos, Filters(text = "zzz nonesiste")).isEmpty())

    // elenchi per i chip
    check(QsoQuery.bandsOf(qsos) == listOf("160M", "40M", "20M", "15M")) { QsoQuery.bandsOf(qsos).toString() }
    check(QsoQuery.yearsOf(qsos) == listOf("2026", "2025")) { QsoQuery.yearsOf(qsos).toString() }

    // unione senza doppioni
    val merged = QsoQuery.merge(qsos, AdifParser.parse(adif))
    check(merged.size == 4) { "merge ha prodotto ${merged.size} QSO" }

    // file senza intestazione e con EOR finale mancante
    val senzaHeader = "<CALL:5>IK1AA<QSO_DATE:8>20260101<TIME_ON:4>1000<BAND:2>2m<MODE:2>FM"
    check(AdifParser.parse(senzaHeader).size == 1)

    // file vuoto o spazzatura
    check(AdifParser.parse("").isEmpty())
    check(AdifParser.parse("questo non e' un adif").isEmpty())

    println("OK: ${qsos.size} QSO, tutti i controlli superati")
}
