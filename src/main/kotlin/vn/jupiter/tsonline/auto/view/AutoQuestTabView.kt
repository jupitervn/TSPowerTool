package vn.jupiter.tsonline.auto.view

import javafx.beans.property.SimpleBooleanProperty
import javafx.collections.FXCollections
import javafx.scene.control.ListView
import javafx.scene.control.SelectionMode
import javafx.scene.control.TextField
import javafx.scene.layout.Priority
import tornadofx.*
import vn.jupiter.tsonline.auto.controller.AppController
import vn.jupiter.tsonline.auto.controller.PacketReceivedEvent
import vn.jupiter.tsonline.auto.controller.PacketSentEvent
import vn.jupiter.tsonline.auto.data.*

/**
 * Created by jupiter on 6/13/17.
 */
data class TSPacketCellData(val packet: Packet) {
    val isReceivedPacket = packet !is SendablePacket
    override fun toString(): String {
        return "$packet raw:${packet.byteBuffer.array().toHex()}"
    }
}

class AutoQuestTabView : View("") {
    var packetLogView = find(PacketLogView::class)
    override val root = hbox {

    }

    init {
        with(root) {
            this += packetLogView.apply {
                hgrow = Priority.ALWAYS
            }
        }
    }

    override fun onDock() {
        super.onDock()
    }
}

