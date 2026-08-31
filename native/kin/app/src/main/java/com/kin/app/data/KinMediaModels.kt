package com.kin.app.data

import org.json.JSONArray
import org.json.JSONObject

data class KinPostMedia(
    val id: String,
    val type: String,
    val contentType: String,
    val url: String,
)

fun kinPostMediaToJson(items: List<KinPostMedia>): String {
    val array = JSONArray()
    items.forEach { item ->
        array.put(
            JSONObject()
                .put("id", item.id)
                .put("type", item.type)
                .put("content_type", item.contentType)
                .put("url", item.url),
        )
    }
    return array.toString()
}

fun kinPostMediaFromJson(raw: String): List<KinPostMedia> = runCatching {
    val array = JSONArray(raw.ifBlank { "[]" })
    buildList {
        for (index in 0 until array.length()) {
            val json = array.getJSONObject(index)
            add(
                KinPostMedia(
                    id = json.optString("id"),
                    type = json.optString("type", "image"),
                    contentType = json.optString("content_type"),
                    url = json.optString("url"),
                ),
            )
        }
    }.filter { it.id.isNotBlank() && it.url.isNotBlank() }
}.getOrDefault(emptyList())
