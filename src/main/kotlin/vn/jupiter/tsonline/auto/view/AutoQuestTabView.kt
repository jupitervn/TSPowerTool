package vn.jupiter.tsonline.auto.view

import javafx.scene.control.TextField
import javafx.scene.control.ToggleGroup
import tornadofx.*
import vn.jupiter.tsonline.auto.controller.AutoQuestController
import java.io.File

/**
 * Created by jupiter on 6/13/17.
 */
class AutoQuestTabView : View("") {
    val controller by inject<AutoQuestController>()
    var questNameTF by singleAssign<TextField>()
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

            val defaultGroup = ToggleGroup()
            togglebutton(text = "Auto", group = defaultGroup, selectFirst = false) {
                action {
                    if (isSelected) {
                        controller.startDoAutoQuest()
                    } else {
                        controller.stopAutoQuest()
                    }
                }

            }

            togglebutton(text = "Record",group = defaultGroup, selectFirst = false) {
                selectedProperty().bindBidirectional(controller.isQuestRecording)
                controller.isQuestRecording.addListener { _, old, new ->
                    if (old && !new) {
                        val questDirectory = chooseDirectory("Save quest", owner = currentWindow)
                        controller.saveRecoredQuest(questDirectory)
                    }
                }
            }

            button("Save") {
                action {
                    val dodoFolder = chooseDirectory ("Open dodo script folder")
                    dodoFolder?.let {
                        controller.saveRecoredQuest(dodoFolder)
                    }
                }
            }

            questNameTF = textfield(controller.questNameProperty) {

            }
        }
        listview(controller.currentRunningQuestSteps) {
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
                item("Run From This Step") {
                    action {
                        controller.startDoAutoQuest(selectionModel.selectedIndex)
                    }
                }

                item("Re-run") {
                    action {
                        controller.reRunStep(selectionModel.selectedIndex)
                    }
                }

                item("Delete") {
                    action {
                        controller.deleteQuestAtIndex(selectionModel.selectedIndex)
                    }
                }

                item("Add") {

                }
            }
        }
    }

    init {
        controller.loadDodoQuest(File("/Users/jupiter/Parallels/Game Tools/1_AutoQuest/Cu thu/"))
    }

    override fun onDock() {
        super.onDock()
    }
}

