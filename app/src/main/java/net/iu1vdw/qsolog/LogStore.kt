package net.iu1vdw.qsolog

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Archivio locale del log: un solo file JSON nella memoria privata dell'app. */
object LogStore {

    private const val FILE_NAME = "log.json"

    private fun file(ctx: Context): File = File(ctx.filesDir, FILE_NAME)

    fun load(ctx: Context): List<Qso> {
        val f = file(ctx)
        if (!f.exists()) return emptyList()
        return try {
            val arr = JSONArray(f.readText())
            val out = ArrayList<Qso>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val map = LinkedHashMap<String, String>()
                val keys = o.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    map[k] = o.optString(k, "")
                }
                out.add(Qso(map))
            }
            out
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun save(ctx: Context, list: List<Qso>) {
        val arr = JSONArray()
        for (q in list) {
            val o = JSONObject()
            for ((k, v) in q.fields) o.put(k, v)
            arr.put(o)
        }
        file(ctx).writeText(arr.toString())
    }

    fun clear(ctx: Context) {
        val f = file(ctx)
        if (f.exists()) f.delete()
    }
}
