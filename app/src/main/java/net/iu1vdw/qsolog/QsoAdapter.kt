package net.iu1vdw.qsolog

import android.content.Context
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

class QsoAdapter(private val onClick: (Qso) -> Unit) : RecyclerView.Adapter<QsoAdapter.VH>() {

    private var items: List<Qso> = emptyList()

    fun submit(list: List<Qso>) {
        items = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_qso, parent, false)
        return VH(v)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val q = items[position]
        val ctx = holder.itemView.context

        holder.call.text = q.call
        holder.datetime.text = listOf(q.dateDisplay, q.timeDisplay)
            .filter { it.isNotEmpty() }.joinToString("  ")

        val bandMode = listOf(q.band, q.mode).filter { it.isNotEmpty() }.joinToString(" · ")
        val freq = q.f("FREQ")
        holder.bandmode.text = if (freq.isNotEmpty()) "$bandMode · $freq MHz" else bandMode

        holder.badges.text = badges(ctx, q)

        val note = q.note
        if (note.isEmpty()) {
            holder.note.visibility = View.GONE
        } else {
            holder.note.visibility = View.VISIBLE
            holder.note.text = note
        }

        holder.itemView.setOnClickListener { onClick(q) }
    }

    private fun badges(ctx: Context, q: Qso): CharSequence {
        val sb = SpannableStringBuilder()
        appendBadge(ctx, sb, "LoTW", q.lotwConfirmed)
        sb.append("   ")
        appendBadge(ctx, sb, "QSL↑", q.qslSentDone)
        sb.append("   ")
        appendBadge(ctx, sb, "QSL↓", q.qslRcvdDone)
        return sb
    }

    private fun appendBadge(ctx: Context, sb: SpannableStringBuilder, label: String, on: Boolean) {
        val start = sb.length
        sb.append(label).append(if (on) " ✓" else " –")
        val color = ContextCompat.getColor(ctx, if (on) R.color.badge_ok else R.color.badge_off)
        sb.setSpan(ForegroundColorSpan(color), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val call: TextView = v.findViewById(R.id.call)
        val datetime: TextView = v.findViewById(R.id.datetime)
        val bandmode: TextView = v.findViewById(R.id.bandmode)
        val badges: TextView = v.findViewById(R.id.badges)
        val note: TextView = v.findViewById(R.id.note)
    }
}
