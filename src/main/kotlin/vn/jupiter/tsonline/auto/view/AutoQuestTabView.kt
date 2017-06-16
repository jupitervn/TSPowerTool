package vn.jupiter.tsonline.auto.view

import javafx.beans.Observable
import javafx.beans.property.SimpleBooleanProperty
import javafx.collections.FXCollections
import javafx.collections.transformation.FilteredList
import javafx.scene.control.ListView
import javafx.scene.control.SelectionMode
import javafx.scene.layout.Priority
import tornadofx.*
import vn.jupiter.tsonline.auto.controller.AppController
import vn.jupiter.tsonline.auto.controller.PacketReceivedEvent
import vn.jupiter.tsonline.auto.controller.PacketSentEvent
import vn.jupiter.tsonline.auto.data.TSPacket
import vn.jupiter.tsonline.auto.data.UnProcessedPacket
import vn.jupiter.tsonline.auto.data.toHex

/**
 * Created by jupiter on 6/13/17.
 */
data class TSPacketCellData(val packet: TSPacket, val isReceivedPacket: Boolean = false) {
    override fun toString(): String {
        return packet.toString()
    }
}

class AutoQuestTabView : View("") {
    val appController: AppController by inject()
    var packetListView by singleAssign<ListView<TSPacketCellData>>()
    val logPackets = FXCollections.observableArrayList<TSPacketCellData>()
    var shouldShowSendPackets = SimpleBooleanProperty(true)
    var shouldShowReceivePackets = SimpleBooleanProperty(true)
    var shouldShowKnownPackets = SimpleBooleanProperty(true)

    override val root = hbox {
        vbox {
            button("Clear log") {
                action {
                    logPackets.clear()
                }
            }
            packetListView = listview {
                vgrow = Priority.ALWAYS
                selectionModel.selectionMode = SelectionMode.SINGLE
                contextmenu {
                    item("Resend") {
                        action {
                            val selectedItem = selectionModel.selectedItem
                            if (selectedItem != null) {
                                appController.sendPacket(selectedItem.packet)
                            }
                        }
                    }

                    item("Copy") {
                        action {
                            val selectedItem = selectionModel.selectedItem
                            if (selectedItem != null) {
                                clipboard.putString(selectedItem.packet.byteBuffer.array().toHex())
                            }
                        }
                    }

                    item("Convert to string") {
                        action {

                        }
                    }
                }

                cellFormat { packetData ->
                    if (!packetData.isReceivedPacket) {
                        style = "-fx-background-color:#00008b; -fx-text-fill:white"
                    } else {
                        style = "-fx-background-color:#00000000; -fx-text-fill:black"
                    }
                    text = "$index - $packetData"

                }
            }
            hbox {
                spacing = 20.0
                checkbox("Send packets", shouldShowSendPackets) {

                }

                checkbox("Receive packets", shouldShowReceivePackets) {

                }

                checkbox("Known packets", shouldShowKnownPackets)
            }
            hboxConstraints {
                hgrow = Priority.ALWAYS
            }
        }

    }

    init {
        subscribe<PacketReceivedEvent> { packetReceivedEvent ->
            logPackets += TSPacketCellData(packetReceivedEvent.packet, true)
//            packetListView.scrollTo(packetListView.items.size)
        }
        subscribe<PacketSentEvent> { packetSentEvent ->
            logPackets += TSPacketCellData(packetSentEvent.packet)
//            packetListView.scrollTo(packetListView.items.size)
        }

        val filteredList = SortedFilteredList<TSPacketCellData>(logPackets)
        filteredList.bindTo(packetListView)
        filteredList.predicate = {
            if (it.isReceivedPacket) {
                shouldShowReceivePackets.get()
            } else {
                shouldShowSendPackets.get()
            } && (shouldShowKnownPackets.get() || it.packet is UnProcessedPacket)
        }

        shouldShowKnownPackets.onChange {
            filteredList.refilter()
        }

        shouldShowReceivePackets.onChange {
            filteredList.refilter()
        }

        shouldShowSendPackets.onChange {
            filteredList.refilter()
        }

    }

    override fun onDock() {
        super.onDock()
    }
}