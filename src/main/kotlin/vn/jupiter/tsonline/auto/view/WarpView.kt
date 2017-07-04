package vn.jupiter.tsonline.auto.view

import javafx.scene.control.ComboBox
import javafx.scene.control.TextArea
import javafx.scene.control.TextField
import javafx.scene.control.ToggleGroup
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
                isEditable = false
                hboxConstraints {
                    hgrow = Priority.SOMETIMES
                }
                textProperty().bind(warpController.warpingLogs)
            }
            vbox {
                mapIds = combobox<MapData>(values = warpController.allMaps) {
                    promptText = "Input map"
                    prefWidth = 200.0
                    makeAutocompletable { keyword ->
                        warpController.allMaps.filter {
                            "$it.id".contains(keyword) || it.name?.toLowerCase()!!.contains(keyword)
                        }
                    }
                }
                togglebutton("Go", selectFirst = false, group = ToggleGroup()) {
                    vboxConstraints {
                        hgrow = Priority.ALWAYS
                    }
                    selectedProperty().bindBidirectional(warpController.isWarpingProperty)
                    val stateText = selectedProperty().stringBinding {
                        if (it == true) "Warping" else "Go"
                    }
                    textProperty().bind(stateText)
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
