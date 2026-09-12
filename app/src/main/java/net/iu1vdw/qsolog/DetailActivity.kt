package net.iu1vdw.qsolog

import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import org.json.JSONObject

class DetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_FIELDS = "fields"

        /** Ordine di presentazione: prima le colonne del log, poi il resto. */
        private val PRIMARY = listOf(
            "CALL" to "CALL",
            "QSO_DATE" to "DATA",
            "TIME_ON" to "ORA",
            "BAND" to "BANDA",
            "MODE" to "MODO",
            "SUBMODE" to "SOTTOMODO",
            "FREQ" to "FREQUENZA (MHz)",
            "RST_SENT" to "RST INVIATO",
            "RST_RCVD" to "RST RICEVUTO",
            "NAME" to "NOME",
            "QTH" to "QTH",
            "GRIDSQUARE" to "LOCATORE",
            "COUNTRY" to "PAESE",
            "DXCC" to "DXCC",
            "CONT" to "CONTINENTE",
            "CQZ" to "ZONA CQ",
            "ITUZ" to "ZONA ITU",
            "COMMENT" to "NOTE",
            "NOTES" to "NOTE (estese)",
            "LOTW_QSL_RCVD" to "LOTW",
            "LOTW_QSL_SENT" to "LOTW (inviato)",
            "QSL_SENT" to "QSL SPED",
            "QSL_SENT_VIA" to "QSL SPED VIA",
            "QSL_RCVD" to "QSL RIC",
            "QSL_RCVD_VIA" to "QSL RIC VIA",
            "QSLSDATE" to "DATA QSL SPED",
            "QSLRDATE" to "DATA QSL RIC",
            "EQSL_QSL_RCVD" to "eQSL",
            "TX_PWR" to "POTENZA (W)",
            "STATION_CALLSIGN" to "MIO NOMINATIVO",
            "MY_GRIDSQUARE" to "MIO LOCATORE",
            "OPERATOR" to "OPERATORE",
            "CONTEST_ID" to "CONTEST"
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detail)

        val toolbar: MaterialToolbar = findViewById(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        val container: LinearLayout = findViewById(R.id.container)

        val raw = intent.getStringExtra(EXTRA_FIELDS) ?: "{}"
        val map = LinkedHashMap<String, String>()
        try {
            val o = JSONObject(raw)
            val keys = o.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val v = o.optString(k, "")
                if (v.isNotEmpty()) map[k] = v
            }
        } catch (e: Exception) {
            // niente da mostrare
        }

        val qso = Qso(map)
        toolbar.title = if (qso.call.isNotEmpty()) qso.call else getString(R.string.detail_title)
        toolbar.subtitle = listOf(qso.dateDisplay, qso.timeDisplay, qso.band, qso.mode)
            .filter { it.isNotEmpty() }.joinToString(" · ")

        val shown = HashSet<String>()
        for ((key, label) in PRIMARY) {
            val value = map[key] ?: continue
            shown.add(key)
            container.addView(row(label, prettify(key, value)))
        }

        val rest = map.keys.filter { it !in shown }.sorted()
        if (rest.isNotEmpty()) {
            container.addView(header("ALTRI CAMPI ADIF"))
            for (key in rest) container.addView(row(key, map[key] ?: ""))
        }
    }

    private fun prettify(key: String, value: String): String = when (key) {
        "QSO_DATE" -> if (value.length == 8)
            value.substring(6, 8) + "/" + value.substring(4, 6) + "/" + value.substring(0, 4) else value
        "TIME_ON", "TIME_OFF" -> if (value.length >= 4)
            value.substring(0, 2) + ":" + value.substring(2, 4) else value
        "LOTW_QSL_RCVD", "QSL_RCVD", "EQSL_QSL_RCVD" -> when (value.uppercase()) {
            "Y", "V" -> "Confermato (${value.uppercase()})"
            "N" -> "No"
            "R" -> "Richiesto"
            "I" -> "Ignora"
            else -> value
        }
        "QSL_SENT" -> when (value.uppercase()) {
            "Y" -> "Spedita"
            "N" -> "No"
            "Q" -> "In coda"
            "R" -> "Richiesta"
            "I" -> "Ignora"
            else -> value
        }
        else -> value
    }

    private fun dp(v: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics
    ).toInt()

    private fun header(text: String): TextView {
        val tv = TextView(this)
        tv.text = text
        tv.textSize = 12f
        tv.alpha = 0.6f
        tv.setPadding(0, dp(20), 0, dp(6))
        return tv
    }

    private fun row(label: String, value: String): LinearLayout {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.setPadding(0, dp(6), 0, dp(6))

        val l = TextView(this)
        l.text = label
        l.textSize = 13f
        l.alpha = 0.7f
        l.gravity = Gravity.START
        l.layoutParams = LinearLayout.LayoutParams(dp(140), LinearLayout.LayoutParams.WRAP_CONTENT)

        val v = TextView(this)
        v.text = value
        v.textSize = 15f
        v.setTextIsSelectable(true)
        v.layoutParams = LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
        )

        row.addView(l)
        row.addView(v)
        return row
    }
}
