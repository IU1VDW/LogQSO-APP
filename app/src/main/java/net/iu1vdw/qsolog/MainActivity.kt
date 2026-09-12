package net.iu1vdw.qsolog

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.chip.Chip
import org.json.JSONObject
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var toolbar: MaterialToolbar
    private lateinit var search: EditText
    private lateinit var chipBand: Chip
    private lateinit var chipMode: Chip
    private lateinit var chipYear: Chip
    private lateinit var chipConf: Chip
    private lateinit var counters: TextView
    private lateinit var list: RecyclerView
    private lateinit var empty: TextView

    private val adapter = QsoAdapter { openDetail(it) }
    private val io = Executors.newSingleThreadExecutor()
    private val ui = Handler(Looper.getMainLooper())

    private var all: List<Qso> = emptyList()
    private var filters = Filters()
    private var optionsMenu: Menu? = null

    private val openDocument =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri != null) importUri(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        search = findViewById(R.id.search)
        chipBand = findViewById(R.id.chipBand)
        chipMode = findViewById(R.id.chipMode)
        chipYear = findViewById(R.id.chipYear)
        chipConf = findViewById(R.id.chipConf)
        counters = findViewById(R.id.counters)
        list = findViewById(R.id.list)
        empty = findViewById(R.id.empty)

        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter
        list.addItemDecoration(DividerItemDecoration(this, DividerItemDecoration.VERTICAL))

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                filters = filters.copy(text = s?.toString() ?: "")
                refresh()
            }
        })

        chipBand.setOnClickListener { pickMulti("Banda", QsoQuery.bandsOf(all), filters.bands) { filters = filters.copy(bands = it); refresh() } }
        chipMode.setOnClickListener { pickMulti("Modo", QsoQuery.modesOf(all), filters.modes) { filters = filters.copy(modes = it); refresh() } }
        chipYear.setOnClickListener { pickMulti("Anno", QsoQuery.yearsOf(all), filters.years) { filters = filters.copy(years = it); refresh() } }
        chipConf.setOnClickListener { pickConf() }

        loadLog { handleIntent(intent) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    // ---------- caricamento e import ----------

    private fun loadLog(after: (() -> Unit)? = null) {
        io.execute {
            val loaded = LogStore.load(this)
            ui.post {
                all = loaded
                refresh()
                after?.invoke()
            }
        }
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        val uri: Uri? = when (intent.action) {
            Intent.ACTION_SEND -> getStreamExtra(intent)
            Intent.ACTION_SEND_MULTIPLE -> getStreamListExtra(intent)
            Intent.ACTION_VIEW -> intent.data
            else -> null
        }
        if (uri != null) {
            intent.action = null
            importUri(uri)
            return
        }
        // Log incollato come testo semplice.
        if (intent.action == Intent.ACTION_SEND) {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (!text.isNullOrBlank() && text.contains("<eor>", ignoreCase = true)) {
                intent.action = null
                importText(text, "testo condiviso")
            }
        }
    }

    private fun getStreamExtra(intent: Intent): Uri? =
        if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
        }

    private fun getStreamListExtra(intent: Intent): Uri? {
        val listUris = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
        }
        return listUris?.firstOrNull()
    }

    private fun importUri(uri: Uri) {
        Toast.makeText(this, "Lettura del file in corso…", Toast.LENGTH_SHORT).show()
        io.execute {
            val text = try {
                contentResolver.openInputStream(uri)?.use { input -> decode(input.readBytes()) }
            } catch (e: Exception) {
                null
            }
            ui.post {
                if (text.isNullOrEmpty()) {
                    Toast.makeText(this, "Non riesco a leggere il file", Toast.LENGTH_LONG).show()
                } else {
                    importText(text, uri.lastPathSegment ?: "file")
                }
            }
        }
    }

    private fun decode(bytes: ByteArray): String {
        val utf8 = String(bytes, Charsets.UTF_8)
        return if (utf8.contains('�')) String(bytes, Charsets.ISO_8859_1) else utf8
    }

    private fun importText(text: String, source: String) {
        io.execute {
            val parsed = AdifParser.parse(text)
            ui.post {
                if (parsed.isEmpty()) {
                    Toast.makeText(this, "Nessun QSO trovato in questo file", Toast.LENGTH_LONG).show()
                    return@post
                }
                if (all.isEmpty()) {
                    commit(parsed, "${parsed.size} QSO importati")
                    return@post
                }
                AlertDialog.Builder(this)
                    .setTitle("Importazione")
                    .setMessage("${parsed.size} QSO letti da $source.\nCosa faccio con il log gia' in archivio (${all.size} QSO)?")
                    .setPositiveButton("Unisci") { _, _ ->
                        val merged = QsoQuery.merge(all, parsed)
                        commit(merged, "Ora ci sono ${merged.size} QSO")
                    }
                    .setNegativeButton("Sostituisci") { _, _ ->
                        commit(parsed, "${parsed.size} QSO importati")
                    }
                    .setNeutralButton("Annulla", null)
                    .show()
            }
        }
    }

    private fun commit(newList: List<Qso>, message: String) {
        all = newList
        filters = filters.copy(bands = emptySet(), modes = emptySet(), years = emptySet())
        refresh()
        io.execute { LogStore.save(this, newList) }
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    // ---------- filtri ----------

    private fun pickMulti(
        title: String,
        values: List<String>,
        selected: Set<String>,
        onDone: (Set<String>) -> Unit
    ) {
        if (values.isEmpty()) {
            Toast.makeText(this, "Nessun dato disponibile", Toast.LENGTH_SHORT).show()
            return
        }
        val items = values.toTypedArray()
        val checked = BooleanArray(items.size) { items[it] in selected }
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMultiChoiceItems(items, checked) { _, which, isChecked -> checked[which] = isChecked }
            .setPositiveButton("Applica") { _, _ ->
                val out = HashSet<String>()
                for (i in items.indices) if (checked[i]) out.add(items[i])
                onDone(out)
            }
            .setNeutralButton("Azzera") { _, _ -> onDone(emptySet()) }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun pickConf() {
        val options = ConfFilter.values()
        val labels = options.map { it.label }.toTypedArray()
        val current = options.indexOf(filters.conf)
        AlertDialog.Builder(this)
            .setTitle("Conferme")
            .setSingleChoiceItems(labels, current) { dialog, which ->
                filters = filters.copy(conf = options[which])
                refresh()
                dialog.dismiss()
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun refresh() {
        val shown = QsoQuery.apply(all, filters)
        adapter.submit(shown)

        val confirmed = shown.count { it.lotwConfirmed }
        counters.text = if (all.isEmpty()) ""
        else "${all.size} QSO in archivio · ${shown.size} mostrati · $confirmed confermati LoTW"

        supportActionBar?.subtitle = "Ordine: ${filters.sort.label}${if (filters.reversed) " ↓" else " ↑"}"

        chipBand.text = chipLabel(getString(R.string.chip_band), filters.bands)
        chipMode.text = chipLabel(getString(R.string.chip_mode), filters.modes)
        chipYear.text = chipLabel(getString(R.string.chip_year), filters.years)
        chipConf.text = if (filters.conf == ConfFilter.ALL) getString(R.string.chip_conf) else filters.conf.label

        when {
            all.isEmpty() -> {
                empty.visibility = View.VISIBLE
                empty.text = getString(R.string.empty_body)
                list.visibility = View.GONE
            }
            shown.isEmpty() -> {
                empty.visibility = View.VISIBLE
                empty.text = getString(R.string.empty_filtered)
                list.visibility = View.GONE
            }
            else -> {
                empty.visibility = View.GONE
                list.visibility = View.VISIBLE
                list.scrollToPosition(0)
            }
        }
    }

    private fun chipLabel(base: String, sel: Set<String>): String = when {
        sel.isEmpty() -> base
        sel.size == 1 -> "$base: ${sel.first()}"
        else -> "$base: ${sel.size}"
    }

    // ---------- menu ----------

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        optionsMenu = menu
        syncMenu()
        return true
    }

    private fun syncMenu() {
        val menu = optionsMenu ?: return
        menu.findItem(R.id.action_reverse)?.isChecked = filters.reversed
        val id = when (filters.sort) {
            SortField.DATE -> R.id.sort_date
            SortField.BAND -> R.id.sort_band
            SortField.MODE -> R.id.sort_mode
            SortField.CALL -> R.id.sort_call
            SortField.NOTE -> R.id.sort_note
            SortField.LOTW -> R.id.sort_lotw
            SortField.QSL_SENT -> R.id.sort_qsl_sent
            SortField.QSL_RCVD -> R.id.sort_qsl_rcvd
        }
        menu.findItem(R.id.action_sort)?.subMenu?.findItem(id)?.isChecked = true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.sort_date -> setSort(SortField.DATE)
            R.id.sort_band -> setSort(SortField.BAND)
            R.id.sort_mode -> setSort(SortField.MODE)
            R.id.sort_call -> setSort(SortField.CALL)
            R.id.sort_note -> setSort(SortField.NOTE)
            R.id.sort_lotw -> setSort(SortField.LOTW)
            R.id.sort_qsl_sent -> setSort(SortField.QSL_SENT)
            R.id.sort_qsl_rcvd -> setSort(SortField.QSL_RCVD)
            R.id.action_reverse -> {
                filters = filters.copy(reversed = !filters.reversed)
                syncMenu()
                refresh()
            }
            R.id.action_import -> openDocument.launch(arrayOf("*/*"))
            R.id.action_clear -> confirmClear()
            R.id.action_about -> about()
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    private fun setSort(field: SortField) {
        filters = filters.copy(sort = field)
        syncMenu()
        refresh()
    }

    private fun confirmClear() {
        AlertDialog.Builder(this)
            .setTitle(R.string.menu_clear)
            .setMessage("Elimino tutti i QSO memorizzati nell'app? Il file originale non viene toccato.")
            .setPositiveButton("Svuota") { _, _ ->
                all = emptyList()
                filters = Filters()
                search.setText("")
                LogStore.clear(this)
                refresh()
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun about() {
        AlertDialog.Builder(this)
            .setTitle(R.string.app_name)
            .setMessage(
                "Consultazione del log ADIF: sola lettura, nessuna modifica ai QSO.\n\n" +
                    "Per aggiornare il log, inoltra un nuovo file .adi verso questa app " +
                    "(Telegram, mail, gestore file) e scegli “Sostituisci” oppure “Unisci”.\n\n" +
                    "IU1VDW"
            )
            .setPositiveButton("Chiudi", null)
            .show()
    }

    private fun openDetail(q: Qso) {
        val o = JSONObject()
        for ((k, v) in q.fields) o.put(k, v)
        val i = Intent(this, DetailActivity::class.java)
        i.putExtra(DetailActivity.EXTRA_FIELDS, o.toString())
        startActivity(i)
    }
}
