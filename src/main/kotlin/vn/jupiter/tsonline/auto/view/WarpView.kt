package vn.jupiter.tsonline.auto.view

import javafx.scene.control.ComboBox
import javafx.scene.control.TextArea
import javafx.scene.control.TextField
import javafx.scene.layout.Priority
import tornadofx.*
import vn.jupiter.tsonline.auto.controller.WarpController
import vn.jupiter.tsonline.auto.data.MapData


/**
 * Created by jupiter on 6/27/17.
 */
class WarpView : View() {
    val warpController by inject<WarpController>()
    var searchTF by singleAssign<TextField>()
    var mapIds by singleAssign<ComboBox<MapData>>()
    var logArea by singleAssign<TextArea>()
    override val root = vbox {
        //        treeview {
//            vboxConstraints {
//                vgrow = Priority.ALWAYS
//            }
//        }
        hbox {
            logArea = textarea {
                hboxConstraints {
                    hgrow = Priority.ALWAYS
                }
            }
            vbox {
                mapIds = combobox<MapData>(values = warpController.allMaps) {
                    promptText = "Input map"
                    makeAutocompletable { keyword ->
                        warpController.allMaps.filter {
                            "$it.id".contains(keyword) || it.name?.toLowerCase()!!.contains(keyword)
                        }
                    }
                }
                button("Go") {
                    vboxConstraints {
                        hgrow = Priority.ALWAYS
                    }
                    action {
                        warpController.warpTo(mapIds.selectedItem)
                    }
                }
            }
        }
    }

    init {

    }
}
