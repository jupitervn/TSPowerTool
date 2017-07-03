package vn.jupiter.tsonline.auto.view

import javafx.beans.binding.Bindings
import javafx.beans.property.SimpleBooleanProperty
import javafx.collections.FXCollections
import javafx.scene.control.ListView
import javafx.scene.control.SelectionMode
import javafx.scene.control.TextField
import javafx.scene.control.ToggleGroup
import javafx.scene.layout.Priority
import tornadofx.*
import vn.jupiter.tsonline.auto.controller.AppController
import vn.jupiter.tsonline.auto.controller.AutoQuestController
import vn.jupiter.tsonline.auto.controller.PacketReceivedEvent
import vn.jupiter.tsonline.auto.controller.PacketSentEvent
import vn.jupiter.tsonline.auto.data.*

/**
 * Created by jupiter on 6/13/17.
 */
class AutoQuestTabView : View("") {
    val controller by inject<AutoQuestController>()
    override val root = vbox {
        hbox {
            spacing = 10.0
            button("Dodo") {
                action {
                    val dodoFolder = chooseDirectory ("Open dodo script folder")
                    dodoFolder?.let {
                        controller.loadDodoQuest(it)
                    }
                }
            }

            button("Load") {

            }

            val defaultGroup = ToggleGroup()
            togglebutton(group = defaultGroup, selectFirst = false) {
                action {
                    if (isSelected) {
                        controller.startDoAutoQuest()
                    } else {
                        controller.stopAutoQuest()
                    }
                }

            }

            togglebutton(group = defaultGroup) {
                text("Record")
                selectedProperty().bindBidirectional(controller.isQuestRecording)

            }
        }
        listview(controller.questSteps) {
            cellFormat {
                controller.currentStep.onChange {
                    if (isSelected) {
                        style = ""
                    } else {
                        if (index == it) {
                            style = "-fx-background-color:#00008b; -fx-text-fill:white"
                        } else {
                            style = "-fx-background-color:#00000000; -fx-text-fill:black"
                        }
                    }
                }
                text = "$index - $it"
            }

            contextmenu {
                menu("Run From This Step") {

                }

                menu("ReRun") {

                }
            }
        }
    }

    init {
    }

    override fun onDock() {
        super.onDock()
    }
}

