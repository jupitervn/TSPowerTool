package vn.jupiter.tsonline.auto.view

import javafx.beans.property.SimpleBooleanProperty
import javafx.collections.FXCollections
import javafx.scene.Parent
import javafx.scene.control.ListView
import javafx.scene.control.SelectionMode
import javafx.scene.control.TextField
import javafx.scene.layout.Priority
import sun.jvm.hotspot.oops.Klass
import tornadofx.*
import vn.jupiter.tsonline.auto.app.Styles
import vn.jupiter.tsonline.auto.controller.*
import vn.jupiter.tsonline.auto.data.*
import kotlin.reflect.KClass

/**
 * Created by jupiter on 6/13/17.
 */

data class TSPacketCellData(val packet: Packet) {
    val isReceivedPacket = packet !is SendablePacket
    override fun toString(): String {
        return "$packet raw:${packet.byteBuffer.array().toHex()}"
    }
}

class PacketLogView : View() {
    val mainController by inject<MainController>()

    var packetListView by singleAssign<ListView<TSPacketCellData>>()
    var filterListView by singleAssign<ListView<KClass<out Packet>>>()
    var commandTF by singleAssign<TextField>()
    var shouldShowSendPackets = SimpleBooleanProperty(true)
    var shouldShowReceivePackets = SimpleBooleanProperty(true)

    override val root = vbox {
        hbox {
            button("Clear log") {
                action {
                    mainController.logPackets.clear()
                }
            }

            commandTF = textfield {

            }

            button("Send") {
                action {
                    val hexStrings = commandTF.text.split(" ")
                    val byteArray = ByteArray(hexStrings.size)
                    hexStrings.forEachIndexed { idx, hexString ->
                        byteArray[idx] = Integer.parseInt(hexString, 16).toByte()
                    }

                    mainController.tsFunction.send(RawSendablePacket(byteArray))
                }
            }
        }
        hbox {
            vgrow = Priority.ALWAYS
            packetListView = listview {
                hboxConstraints {
                    hgrow = Priority.ALWAYS
                }
                selectionModel.selectionMode = SelectionMode.SINGLE
                contextmenu {
                    item("Resend") {
                        action {
                            val selectedItem = selectionModel.selectedItem
                            if (selectedItem != null && !selectedItem.isReceivedPacket) {
                                mainController.tsFunction.send(selectedItem.packet as SendablePacket)
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
                    if (packetData.packet is SendablePacket) {
                        style = "-fx-background-color:#00008b; -fx-text-fill:white"
                    } else {
                        style = "-fx-background-color:#00000000; -fx-text-fill:black"
                    }
                    text = "$index - $packetData"

                }
            }

            filterListView = listview {
                selectionModel.selectionMode = SelectionMode.MULTIPLE
                cellFormat {
                    text = it.simpleName
                }

            }
        }
        hbox {
            spacing = 20.0
            checkbox("Send packets", shouldShowSendPackets) {

            }

            checkbox("Receive packets", shouldShowReceivePackets) {

            }

        }
    }

    init {
        val knownPackets = receivedPacketRegistry.values + sentPacketRegistry.values + RawPacket::class + RawSendablePacket::class
        filterListView.items = FXCollections.observableList(knownPackets)
        filterListView.selectionModel.selectAll()

        val filteredList = SortedFilteredList<TSPacketCellData>(mainController.logPackets)
        filteredList.bindTo(packetListView)
        filteredList.predicate = {
            if (it.isReceivedPacket) {
                shouldShowReceivePackets.get()
            } else {
                shouldShowSendPackets.get()
            } && (filterListView.selectionModel.selectedItems.contains(it.packet::class))
        }

        shouldShowReceivePackets.onChange {
            filteredList.refilter()
        }

        shouldShowSendPackets.onChange {
            filteredList.refilter()
        }

        filterListView.selectionModel.selectedItems.onChange {
            filteredList.refilter()
        }

    }
}
