package vn.jupiter.tsonline.auto.view

import javafx.beans.binding.Bindings
import javafx.beans.property.SimpleStringProperty
import javafx.util.converter.NumberStringConverter
import vn.jupiter.tsonline.auto.app.Styles
import tornadofx.*
import vn.jupiter.tsonline.auto.controller.AppController

class MainView : View("TSPower Tool") {
    val autoQuestView = find(AutoQuestTabView::class)
    val warpView = find(WarpView::class)
    val controller: AppController by inject()
    override val root = vbox {
        label("") {
            addClass(Styles.heading)
        }

        tabpane {
            tab("AutoQuest", autoQuestView.root)
            tab("Warp", warpView.root)
            tab("Environment")
            prefWidth = 500.0
            prefHeight = 750.0
        }

        hbox {
            label("Mapname") {
            }
            label("MapId") {
                textProperty().bindBidirectional(controller.tsChar.mapId, NumberStringConverter())
            }
        }
    }

    init {
        runAsync {
            controller.loadStaticData()
        } ui {
            controller.onStart()
        }
    }

    override fun onUndock() {
        super.onUndock()
    }
}