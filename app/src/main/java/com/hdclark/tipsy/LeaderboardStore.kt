package com.hdclark.tipsy

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class LeaderboardStore(context: Context) {
    private val prefs = context.getSharedPreferences("tipsy_race_history", Context.MODE_PRIVATE)

    fun loadHistory(): List<RaceHistoryEntry> {
        val encoded = prefs.getString(KEY, "[]") ?: "[]"
        val array = JSONArray(encoded)
        return buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val ts = item.optLong("epoch", 0L)
                val podiumJson = item.optJSONArray("podium") ?: JSONArray()
                val podium = buildList {
                    for (j in 0 until podiumJson.length()) {
                        add(podiumJson.optInt(j))
                    }
                }
                add(RaceHistoryEntry(ts, podium))
            }
        }
    }

    fun append(entry: RaceHistoryEntry) {
        val history = loadHistory().toMutableList()
        history.add(entry)
        val encoded = JSONArray()
        history.takeLast(MAX_RECORDS).forEach {
            encoded.put(
                JSONObject()
                    .put("epoch", it.epochMillis)
                    .put("podium", JSONArray(it.podiumBallIds))
            )
        }
        prefs.edit().putString(KEY, encoded.toString()).apply()
    }

    companion object {
        private const val KEY = "history"
        private const val MAX_RECORDS = 100
    }
}
