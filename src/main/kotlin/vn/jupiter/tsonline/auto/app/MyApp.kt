package vn.jupiter.tsonline.auto.app

import javafx.stage.Stage
import tornadofx.*
import vn.jupiter.tsonline.auto.controller.AppController
import vn.jupiter.tsonline.auto.view.MainView

class MyApp: App(MainView::class, Styles::class) {
    override fun start(stage: Stage) {
        super.start(stage)
        stage.isAlwaysOnTop = true
    }

    override fun stop() {
        super.stop()
    }
}