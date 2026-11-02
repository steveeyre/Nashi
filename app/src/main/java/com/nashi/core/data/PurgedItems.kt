package com.nashi.core.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.trashPrefs by preferencesDataStore(name = "trash_prefs")
private val PURGED_IDS = stringSetPreferencesKey("purged_item_ids")

/** 本机彻底删除过(硬删)的物品 id: 同步拉取时用来忽略服务端残留的墓碑。 */
internal suspend fun Context.addPurgedItem(id: String) {
    trashPrefs.edit { prefs ->
        prefs[PURGED_IDS] = (prefs[PURGED_IDS] ?: emptySet()) + id
    }
}

internal suspend fun Context.purgedItemIds(): Set<String> =
    trashPrefs.data.first()[PURGED_IDS] ?: emptySet()
