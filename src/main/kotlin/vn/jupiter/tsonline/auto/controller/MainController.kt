package vn.jupiter.tsonline.auto.controller

import javafx.beans.property.SimpleStringProperty
import javafx.collections.FXCollections
import javafx.util.StringConverter
import tornadofx.*
import vn.jupiter.tsonline.auto.app.ConnectionScope
import vn.jupiter.tsonline.auto.utils.JavaFxScheduler
import vn.jupiter.tsonline.auto.view.TSPacketCellData

/**
 * Created by jupiter on 7/2/17.
 */
class MainController : Controller() {
    val appController by inject<AppController>(DefaultScope)
    val tsFunction: TSFunction = (scope as ConnectionScope).tsFunction
    val logPackets = FXCollections.observableArrayList<TSPacketCellData>()
    val mapNameProperty = SimpleStringProperty()
    init {
        tsFunction.packetsPublisher
                .filter {
                    it.command != 0x1808
                }
                .observeOn(JavaFxScheduler.getInstance())
                .subscribe {
                    logPackets += TSPacketCellData(it)
                }
        mapNameProperty.bindBidirectional(tsFunction.tsChar.mapId, object: StringConverter<Number>() {
            override fun fromString(string: String?): Number {
                return 0
            }

            override fun toString(mapId: Number?): String {
                return appController.mapList[mapId?.toInt()]?.name ?: mapId.toString()
            }

        })
    }

}