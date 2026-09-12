package net.iu1vdw.qsolog

/** Le colonne su cui si puo' ordinare. */
enum class SortField(val label: String) {
    DATE("DATA / ORA"),
    BAND("BANDA"),
    MODE("MODO"),
    CALL("CALL"),
    NOTE("NOTE"),
    LOTW("LOTW"),
    QSL_SENT("QSL SPED"),
    QSL_RCVD("QSL RIC")
}

/** Filtro sulle conferme (a scelta singola). */
enum class ConfFilter(val label: String) {
    ALL("Tutti i QSO"),
    LOTW_OK("Solo confermati LoTW"),
    LOTW_NO("Solo NON confermati LoTW"),
    QSL_SENT("Solo con QSL spedita"),
    QSL_RCVD("Solo con QSL ricevuta"),
    QSL_MISSING("Solo senza nessuna conferma")
}

data class Filters(
    val text: String = "",
    val bands: Set<String> = emptySet(),
    val modes: Set<String> = emptySet(),
    val years: Set<String> = emptySet(),
    val conf: ConfFilter = ConfFilter.ALL,
    val sort: SortField = SortField.DATE,
    /** Di base la lista parte dal QSO piu' recente. */
    val reversed: Boolean = true
)

object QsoQuery {

    /** Ordine radiantistico delle bande, dalla piu' bassa alla piu' alta. */
    private val BAND_ORDER = listOf(
        "2190M", "630M", "560M", "160M", "80M", "60M", "40M", "30M", "20M", "17M",
        "15M", "12M", "10M", "8M", "6M", "5M", "4M", "2M", "1.25M", "70CM", "33CM",
        "23CM", "13CM", "9CM", "6CM", "3CM", "1.25CM", "6MM", "4MM", "2.5MM", "2MM", "1MM"
    )

    fun bandRank(band: String): Int {
        val idx = BAND_ORDER.indexOf(band.uppercase())
        return if (idx >= 0) idx else BAND_ORDER.size
    }

    fun apply(source: List<Qso>, f: Filters): List<Qso> {
        val needles = f.text.trim().lowercase().split(' ').filter { it.isNotEmpty() }

        val filtered = source.filter { q ->
            if (f.bands.isNotEmpty() && q.band !in f.bands) return@filter false
            if (f.modes.isNotEmpty() && q.mode !in f.modes) return@filter false
            if (f.years.isNotEmpty() && q.year !in f.years) return@filter false
            when (f.conf) {
                ConfFilter.ALL -> {}
                ConfFilter.LOTW_OK -> if (!q.lotwConfirmed) return@filter false
                ConfFilter.LOTW_NO -> if (q.lotwConfirmed) return@filter false
                ConfFilter.QSL_SENT -> if (!q.qslSentDone) return@filter false
                ConfFilter.QSL_RCVD -> if (!q.qslRcvdDone) return@filter false
                ConfFilter.QSL_MISSING ->
                    if (q.lotwConfirmed || q.qslRcvdDone) return@filter false
            }
            if (needles.isNotEmpty()) {
                val blob = q.searchBlob
                for (n in needles) if (!blob.contains(n)) return@filter false
            }
            true
        }

        val cmp: Comparator<Qso> = when (f.sort) {
            SortField.DATE -> compareBy({ it.dateRaw }, { it.timeRaw }, { it.call })
            SortField.BAND -> compareBy({ bandRank(it.band) }, { it.dateRaw }, { it.timeRaw })
            SortField.MODE -> compareBy({ it.mode }, { it.dateRaw }, { it.timeRaw })
            SortField.CALL -> compareBy({ it.call }, { it.dateRaw }, { it.timeRaw })
            SortField.NOTE -> compareBy({ it.note.lowercase() }, { it.dateRaw })
            SortField.LOTW -> compareBy({ if (it.lotwConfirmed) 0 else 1 }, { it.dateRaw })
            SortField.QSL_SENT -> compareBy({ if (it.qslSentDone) 0 else 1 }, { it.dateRaw })
            SortField.QSL_RCVD -> compareBy({ if (it.qslRcvdDone) 0 else 1 }, { it.dateRaw })
        }

        val sorted = filtered.sortedWith(cmp)
        return if (f.reversed) sorted.reversed() else sorted
    }

    /** Unisce due elenchi scartando i doppioni (stesso call/data/ora/banda/modo). */
    fun merge(existing: List<Qso>, incoming: List<Qso>): List<Qso> {
        val seen = HashSet<String>(existing.size * 2)
        val out = ArrayList<Qso>(existing.size + incoming.size)
        for (q in existing) if (seen.add(q.dedupKey)) out.add(q)
        for (q in incoming) if (seen.add(q.dedupKey)) out.add(q)
        return out
    }

    fun bandsOf(list: List<Qso>): List<String> =
        list.map { it.band }.filter { it.isNotEmpty() }.distinct().sortedBy { bandRank(it) }

    fun modesOf(list: List<Qso>): List<String> =
        list.map { it.mode }.filter { it.isNotEmpty() }.distinct().sorted()

    fun yearsOf(list: List<Qso>): List<String> =
        list.map { it.year }.filter { it.isNotEmpty() }.distinct().sortedDescending()
}
