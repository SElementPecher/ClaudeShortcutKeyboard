package io.github.selementpecher.shortcutkeyboard.ime

import android.content.Context
import io.github.selementpecher.shortcutkeyboard.R

/** The words on the ready-made time and date answers. Shared by the keyboard and the editor. */
object QuickLabels {
    /** "Now\n18:13" for 0, otherwise e.g. "15 min ago", "1 hour ago", "in 30 min". [nowText] is the clock time. */
    fun time(context: Context, minutes: Long, nowText: String): String {
        if (minutes == 0L) return context.getString(R.string.kb_time_now, nowText)
        val size = Math.abs(minutes).toInt()
        val ago = minutes < 0
        val hours = size >= 60 && size % 60 == 0
        val count = if (hours) size / 60 else size
        val res = when {
            hours && count == 1 -> if (ago) R.string.kb_time_hour_ago else R.string.kb_time_hour_ahead
            hours -> if (ago) R.string.kb_time_hours_ago else R.string.kb_time_hours_ahead
            else -> if (ago) R.string.kb_time_min_ago else R.string.kb_time_min_ahead
        }
        return context.getString(res, count)
    }

    fun date(context: Context, days: Long): String {
        val size = Math.abs(days).toInt()
        val ago = days < 0
        return when {
            days == 0L -> context.getString(R.string.kb_today)
            days == -1L -> context.getString(R.string.kb_yesterday)
            days == 1L -> context.getString(R.string.kb_tomorrow)
            size == 7 -> context.getString(if (ago) R.string.kb_date_week_ago else R.string.kb_date_week_ahead)
            else -> context.getString(if (ago) R.string.kb_date_days_ago else R.string.kb_date_days_ahead, size)
        }
    }
}
