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
class PacketLogView : View() {
    val appController: AppController by inject()
    var packetListView by singleAssign<ListView<TSPacketCellData>>()
    var filterListView by singleAssign<ListView<KClass<out Packet>>>()
    var commandTF by singleAssign<TextField>()
    val logPackets = FXCollections.observableArrayList<TSPacketCellData>()
    var shouldShowSendPackets = SimpleBooleanProperty(true)
    var shouldShowReceivePackets = SimpleBooleanProperty(true)

    override val root = vbox {
        hbox {
            button("Clear log") {
                action {
                    logPackets.clear()
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

                    appController.sendPacket(RawSendablePacket(byteArray))
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
                                appController.sendPacket(selectedItem.packet as SendablePacket)
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
        subscribe<PacketReceivedEvent> { packetReceivedEvent ->
            if (packetReceivedEvent.packet.command != 0x1808) {
                logPackets += TSPacketCellData(packetReceivedEvent.packet)
            }
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
