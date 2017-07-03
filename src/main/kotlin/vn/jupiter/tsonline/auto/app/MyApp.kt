package vn.jupiter.tsonline.auto.app

import javafx.stage.Stage
import tornadofx.*
import vn.jupiter.tsonline.auto.view.AppView

class MyApp: App(AppView::class, Styles::class) {

    override fun start(stage: Stage) {
        super.start(stage)
        stage.isAlwaysOnTop = true
    }

    override fun stop() {
        super.stop()
    }
}