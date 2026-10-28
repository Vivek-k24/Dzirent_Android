package com.dzirent.app.data

import android.content.Context
import android.util.Log
import com.dzirent.app.util.SearchRelated

/** assets/search_related.json, read once per process. */
object SearchRelatedStore {
    @Volatile
    private var loaded: SearchRelated? = null

    fun get(context: Context): SearchRelated = loaded ?: synchronized(this) {
        loaded ?: read(context).also { loaded = it }
    }

    private fun read(context: Context): SearchRelated = try {
        context.applicationContext.assets.open("search_related.json").bufferedReader().use { SearchRelated.parse(it.readText()) }
    } catch (e: Exception) {
        // Suggestions just skip the related-words step; search itself still works.
        Log.e("Dzirent", "Couldn't read search_related.json: ${e.javaClass.simpleName}")
        SearchRelated(emptyMap(), emptyMap())
    }
}
