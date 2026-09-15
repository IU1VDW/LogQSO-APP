package net.iu1vdw.qsolog

/**
 * Un collegamento. I campi ADIF sono conservati tutti (chiave in MAIUSCOLO) cosi'
 * la schermata di dettaglio puo' mostrare anche quello che non sta nelle colonne.
 */
class Qso(val fields: Map<String, String>) {

    fun f(key: String): String = fields[key] ?: ""

    private fun firstOf(vararg keys: String): String {
        for (k in keys) {
            val v = f(k)
            if (v.isNotEmpty()) return v
        }
        return ""
    }

    val call: String get() = f("CALL").uppercase()

    /** AAAAMMGG come nell'ADIF, usata per ordinare. */
    val dateRaw: String get() = f("QSO_DATE")

    /** HHMMSS normalizzata, usata per ordinare. */
    val timeRaw: String
        get() {
            val t = firstOf("TIME_ON", "TIME_OFF").filter { it.isDigit() }
            return when {
                t.isEmpty() -> ""
                t.length >= 6 -> t.substring(0, 6)
                else -> t.padEnd(6, '0')
            }
        }

    val band: String get() = f("BAND").uppercase()

    /** Se c'e' il SUBMODE (FT4, JS8...) e' piu' informativo del MODE (MFSK). */
    val mode: String
        get() {
            val sub = f("SUBMODE").uppercase()
            val m = f("MODE").uppercase()
            return if (sub.isNotEmpty()) sub else m
        }

    val note: String
        get() = listOf(
            firstOf("COMMENT", "COMMENT_INTL"),
            firstOf("NOTES", "NOTES_INTL"),
            firstOf("QSLMSG", "QSLMSG_INTL")
        ).filter { it.isNotEmpty() }.joinToString(" — ")

    /** Paese (entita' DXCC) come lo scrive il programma di log. */
    val country: String get() = firstOf("COUNTRY", "COUNTRY_INTL")

    val lotw: String get() = f("LOTW_QSL_RCVD").uppercase()
    val lotwSent: String get() = f("LOTW_QSL_SENT").uppercase()
    val qslSent: String get() = f("QSL_SENT").uppercase()
    val qslRcvd: String get() = f("QSL_RCVD").uppercase()

    val year: String get() = if (dateRaw.length >= 4) dateRaw.substring(0, 4) else ""

    val dateDisplay: String
        get() = if (dateRaw.length == 8)
            dateRaw.substring(6, 8) + "/" + dateRaw.substring(4, 6) + "/" + dateRaw.substring(0, 4)
        else dateRaw

    val timeDisplay: String
        get() = if (timeRaw.length >= 4) timeRaw.substring(0, 2) + ":" + timeRaw.substring(2, 4) else timeRaw

    val lotwConfirmed: Boolean get() = lotw == "Y" || lotw == "V"
    val qslSentDone: Boolean get() = qslSent == "Y" || qslSent == "Q" || qslSent == "S"
    val qslRcvdDone: Boolean get() = qslRcvd == "Y" || qslRcvd == "V"

    /** Chiave per riconoscere lo stesso QSO quando si uniscono due esportazioni. */
    val dedupKey: String get() = "$call|$dateRaw|$timeRaw|$band|${f("MODE").uppercase()}"

    /** Testo su cui lavora la ricerca libera: tutti i valori dei campi. */
    val searchBlob: String by lazy {
        val sb = StringBuilder()
        sb.append(dateDisplay).append(' ')
        for (v in fields.values) sb.append(v).append(' ')
        sb.toString().lowercase()
    }
}
