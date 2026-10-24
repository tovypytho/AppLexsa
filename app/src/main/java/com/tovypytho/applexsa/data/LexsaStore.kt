package com.tovypytho.applexsa.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.tovypytho.applexsa.core.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONObject

private val Context.dataStore by preferencesDataStore("lexsa")
class LexsaStore(context: Context) {
    private val store = context.applicationContext.dataStore
    private val profileKey = stringPreferencesKey("profile")
    private val reportKey = stringPreferencesKey("report")
    val profile: Flow<Profile> = store.data.map { decode(it[profileKey]) }
    val report: Flow<String> = store.data.map { it[reportKey].orEmpty() }
    suspend fun update(transform: (Profile) -> Profile) = store.edit { prefs ->
        prefs[profileKey] = encode(transform(decode(prefs[profileKey])))
    }
    suspend fun setReport(report: String) = store.edit { it[reportKey] = report.take(100_000) }
    private fun encode(p: Profile): String = JSONObject().apply {
        put("channel", p.channel); put("enabled", p.enabled); put("lastAttempt", p.lastAttempt)
        p.contact?.let { put("contact", JSONObject().put("name", it.name).put("signature", it.signature)) }
        p.calibration?.let { put("calibration", JSONObject().put("x", it.x).put("y", it.y).put("width", it.width).put("height", it.height).put("layout", it.layout)) }
    }.toString()
    private fun decode(raw: String?): Profile = runCatching {
        val j = JSONObject(raw ?: "{}")
        Profile(j.optString("channel"), j.optJSONObject("contact")?.let { Contact(it.getString("name"), it.getString("signature")) },
            j.optBoolean("enabled"), j.optString("lastAttempt"), j.optJSONObject("calibration")?.let {
                Calibration(it.getInt("x"), it.getInt("y"), it.getInt("width"), it.getInt("height"), it.getString("layout"))
            })
    }.getOrDefault(Profile())
}
