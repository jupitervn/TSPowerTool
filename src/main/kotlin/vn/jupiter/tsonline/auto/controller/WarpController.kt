package vn.jupiter.tsonline.auto.controller

import javafx.collections.FXCollections
import javafx.collections.MapChangeListener
import javafx.collections.ObservableList
import javafx.collections.ObservableMap
import tornadofx.*
import vn.jupiter.tsonline.auto.data.MapData
import vn.jupiter.tsonline.auto.data.WalkDirection

/**
 * Created by jupiter on 6/27/17.
 */
class WarpController : Controller() {
    val appController: AppController by inject()
    val allMaps = FXCollections.observableArrayList<MapData>()

    init {
        appController.mapList.addListener(MapChangeListener<Int, MapData> { change ->
            change?.let {
                if (it.wasAdded()) {
                    allMaps.add(it.valueAdded)
                } else {
                    allMaps.remove(it.valueRemoved)
                }
            }
        })
    }

    fun warpTo(map: MapData?) {
        map?.let {
            appController.warpTo(map.id)
        }
    }
}