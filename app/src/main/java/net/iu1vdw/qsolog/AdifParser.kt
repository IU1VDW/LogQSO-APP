package net.iu1vdw.qsolog

/**
 * Parser ADIF volutamente tollerante: legge <CAMPO:lunghezza[:tipo]>valore, ignora
 * l'intestazione fino a <EOH> e chiude un record a ogni <EOR>. Funziona con gli
 * export di Log4OM, QRZ, Club Log, LoTW e WSJT-X.
 */
object AdifParser {

    fun parse(text: String): List<Qso> {
        val out = ArrayList<Qso>()
        var i = 0

        // Se c'e' un'intestazione, saltala.
        val eoh = indexOfIgnoreCase(text, "<eoh>")
        if (eoh >= 0) i = eoh + 5

        var current = LinkedHashMap<String, String>()
        while (i < text.length) {
            val lt = text.indexOf('<', i)
            if (lt < 0) break
            val gt = text.indexOf('>', lt + 1)
            if (gt < 0) break

            val tag = text.substring(lt + 1, gt)
            val parts = tag.split(':')
            val name = parts[0].trim().uppercase()

            if (name == "EOR") {
                if (current.isNotEmpty()) {
                    out.add(Qso(current))
                    current = LinkedHashMap()
                }
                i = gt + 1
                continue
            }
            if (name == "EOH") {
                current = LinkedHashMap()
                i = gt + 1
                continue
            }

            val len = if (parts.size > 1) parts[1].trim().toIntOrNull() else null
            if (len == null || len < 0) {
                // Tag senza lunghezza: non e' un campo dati, si tira dritto.
                i = gt + 1
                continue
            }

            val start = gt + 1
            val end = if (start + len > text.length) text.length else start + len
            val value = text.substring(start, end).trim()
            if (name.isNotEmpty() && value.isNotEmpty()) current[name] = value
            i = end
        }

        if (current.isNotEmpty()) out.add(Qso(current))
        return out
    }

    private fun indexOfIgnoreCase(text: String, needle: String): Int =
        text.indexOf(needle, 0, ignoreCase = true)
}
