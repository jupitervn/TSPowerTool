package vn.jupiter.tsonline.auto.view

import vn.jupiter.tsonline.auto.app.Styles
import tornadofx.*
import vn.jupiter.tsonline.auto.controller.AppController

class MainView : View("TSPower Tool") {
    val autoQuestView = find(AutoQuestTabView::class)
    override val root = vbox {
        label("") {
            addClass(Styles.heading)
        }

        tabpane {
            tab("AutoQuest", autoQuestView.root)
            tab("Warp")
            tab("Environment")
            prefWidth = 500.0
            prefHeight = 750.0
        }

        hbox {
            label("Mapname")
            label("MapId")
        }
    }

    val controller: AppController by inject()

    init {
        controller.onStart()
    }

    override fun onUndock() {
        super.onUndock()
    }
}