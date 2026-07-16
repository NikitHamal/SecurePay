package com.touchbase.user.worker

import android.content.Context
import com.touchbase.user.data.model.LocationSample
import org.json.JSONArray
import org.json.JSONObject

/**
 * Very small persistent queue for stolen-device location pings.
 *
 * We intentionally keep this SharedPreferences-backed instead of Room so it is
 * available early in managed-device boot and has no schema migration risk.
 */
class LocationReportStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun enqueue(sample: LocationSample) {
        val array = readArray()
        array.put(sample.toJson())
        trimAndSave(array)
    }

    @Synchronized
    fun peek(max: Int): List<LocationSample> {
        val array = readArray()
        val count = minOf(max, array.length())
        val out = ArrayList<LocationSample>(count)
        for (i in 0 until count) {
            array.optJSONObject(i)?.toSample()?.let(out::add)
        }
        return out
    }

    @Synchronized
    fun removeFirst(count: Int) {
        if (count <= 0) return
        val array = readArray()
        val next = JSONArray()
        for (i in count until array.length()) {
            next.put(array.get(i))
        }
        saveArray(next)
    }

    private fun readArray(): JSONArray {
        val raw = prefs.getString(KEY_QUEUE, "[]") ?: "[]"
        return runCatching { JSONArray(raw) }.getOrDefault(JSONArray())
    }

    private fun trimAndSave(array: JSONArray) {
        val start = maxOf(0, array.length() - MAX_QUEUE_SIZE)
        val trimmed = JSONArray()
        for (i in start until array.length()) trimmed.put(array.get(i))
        saveArray(trimmed)
    }

    private fun saveArray(array: JSONArray) {
        prefs.edit().putString(KEY_QUEUE, array.toString()).apply()
    }

    private fun LocationSample.toJson(): JSONObject = JSONObject()
        .put("lat", lat)
        .put("lng", lng)
        .put("accuracy", accuracy)
        .put("battery", battery)
        .put("timestamp", timestamp)

    private fun JSONObject.toSample(): LocationSample? {
        val lat = optDouble("lat", Double.NaN)
        val lng = optDouble("lng", Double.NaN)
        if (!lat.isFinite() || !lng.isFinite()) return null
        val accuracyValue = optDouble("accuracy", Double.NaN)
        val batteryValue = if (has("battery") && !isNull("battery")) optInt("battery") else null
        return LocationSample(
            lat = lat,
            lng = lng,
            accuracy = accuracyValue.takeIf { it.isFinite() },
            battery = batteryValue,
            timestamp = optLong("timestamp", System.currentTimeMillis() / 1000L)
        )
    }

    companion object {
        private const val PREFS_NAME = "securepay_location_queue"
        private const val KEY_QUEUE = "pending_location_logs"
        private const val MAX_QUEUE_SIZE = 500
    }
}
