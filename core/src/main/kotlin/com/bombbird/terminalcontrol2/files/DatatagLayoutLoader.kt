package com.bombbird.terminalcontrol2.files

import com.bombbird.terminalcontrol2.global.DATATAG_LAYOUTS
import com.bombbird.terminalcontrol2.ui.datatag.DatatagConfig
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter

private const val DATATAG_LAYOUT_PATH = "DatatagLayouts"

@OptIn(ExperimentalStdlibApi::class)
val layoutAdapter = Moshi.Builder().build().adapter<DatatagLayoutJSON>()

/** Data class for storing player's datatag layouts, which will be used for serializing to and from JSON */
@JsonClass(generateAdapter = true)
data class DatatagLayoutJSON(
    val name: String,
    val mainArrangement: List<List<String>>,
    val miniFirstArrangement: List<List<String>>,
    val miniSecondArrangement: List<List<String>>,
    val showOnlyWhenChanged: List<String>
)

/** Loads all the datatag layouts in the external directory, setting the global datatag layout hashmap to the results */
fun loadAllDatatagLayouts() {
    val layouts = HashMap<String, DatatagConfig>()
    val dirHandle = getExtDir(DATATAG_LAYOUT_PATH) ?: return
    layouts[DatatagConfig.DEFAULT] = DatatagConfig(DatatagConfig.DEFAULT)
    layouts[DatatagConfig.COMPACT] = DatatagConfig(DatatagConfig.COMPACT)
    if (dirHandle.exists()) {
        for (file in dirHandle.list()) {
            val jsonString = file.readString()
            val layoutJson = layoutAdapter.fromJson(jsonString) ?: continue
            layouts[layoutJson.name] = DatatagConfig(layoutJson.name, layoutJson)
        }
    }
    DATATAG_LAYOUTS = layouts
}

/**
 * Saves the input [DatatagConfig] with the input name to external directory
 * @param config datatag config to save
 */
fun saveDatatagLayout(config: DatatagConfig): Boolean {
    val dirHandle = getExtDir(DATATAG_LAYOUT_PATH) ?: return false
    if (!dirHandle.exists()) dirHandle.mkdirs()
    val file = dirHandle.child("${config.name}.json")
    val layoutJson = DatatagLayoutJSON(config.name, config.arrangement.map { it.toList() }, config.miniArrangementFirst.map { it.toList() },
        config.miniArrangementSecond.map { it.toList() }, config.onlyShowWhenChanged.toList())
    val jsonString = layoutAdapter.toJson(layoutJson)
    file.writeString(jsonString, false)
    return true
}

/**
 * Deletes the datatag layout file with the input name
 * @param name name of the layout to delete
 */
fun deleteDatatagLayout(name: String) {
    val dirHandle = getExtDir(DATATAG_LAYOUT_PATH) ?: return
    if (!dirHandle.exists()) return
    val file = dirHandle.child("$name.json")
    if (file.exists()) file.delete()
}