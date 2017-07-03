package vn.jupiter.tsonline.auto.controller

import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleStringProperty
import javafx.beans.value.ObservableValue
import javafx.collections.FXCollections
import javafx.collections.MapChangeListener
import javafx.collections.ObservableList
import javafx.collections.ObservableMap
import tornadofx.*
import vn.jupiter.tsonline.auto.data.*

/**
 * Created by jupiter on 6/27/17.
 */
class WarpController : Controller() {
    val mainController: MainController by inject()
    val appController = mainController.appController
    val allMaps = FXCollections.observableArrayList<MapData>()
    val isWarpingProperty = SimpleBooleanProperty(false)
    val warpingLogs = SimpleStringProperty("")

    init {
        allMaps.addAll(appController.mapList.values)
        appController.mapList.addListener(MapChangeListener<Int, MapData> { change ->
            change?.let {
                if (it.wasAdded()) {
                    allMaps.add(it.valueAdded)
                } else {
                    allMaps.remove(it.valueRemoved)
                }
            }
        })

        mainController.tsFunction.gameEventPublisher
                .filter { event ->
                    event is WarpingStarted || event is WarpingEnded || event is MapDirection
                }
                .subscribe { gameEvent ->
                    val warpLog = when (gameEvent) {
                        is WarpingEnded -> {
                            isWarpingProperty.set(false)
                            "End Warping"
                        }
                        is WarpingStarted -> {
                            warpingLogs.set("")
                            "Start warping"
                        }
                        is MapDirection -> {
                            "Warp to ${gameEvent.targetId} via ${gameEvent.warpId}"
                        }
                        else -> ""
                    }
                    warpingLogs.set(warpingLogs.value.plus(warpLog).plus("\n"))
                }
    }

    fun warpTo(map: MapData?) {
        map?.let {
            mainController.tsFunction.warpTo(map.id)
        }
    }

}