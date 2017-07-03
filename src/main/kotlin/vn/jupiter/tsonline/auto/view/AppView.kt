package vn.jupiter.tsonline.auto.view

import javafx.application.Platform
import javafx.collections.MapChangeListener
import tornadofx.*
import vn.jupiter.tsonline.auto.app.ConnectionScope
import vn.jupiter.tsonline.auto.controller.AppController
import vn.jupiter.tsonline.auto.controller.MainController
import vn.jupiter.tsonline.auto.controller.TSFunction


/**
 * Created by jupiter on 7/2/17.
 */
class AppView : View("TS Power Tool v1.0") {
    val appController : AppController by inject()
    val mapOfScope = mutableMapOf<String, Scope>()
    override val root = tabpane {
        prefWidth = 500.0
        prefHeight = 750.0
    }

    init {
        runAsync {
            appController.loadStaticData()
        } ui {
            appController.mapConnectionHandler.addListener(MapChangeListener<String, TSFunction> { it ->
                Platform.runLater {
                    val connectionId = it.key.toString()
                    var scope = mapOfScope[connectionId]
                    if (it.wasAdded()) {
                        scope = ConnectionScope(it.valueAdded)
                        mapOfScope[connectionId] = scope
                        val newTabView = find(MainView::class, scope)
                        setInScope(MainController(), scope)
                        setInScope(newTabView, scope)
                        root.tab(connectionId, newTabView.root) {
                            prefWidthProperty().bind(root.prefWidthProperty())
                            prefHeightProperty().bind(root.prefHeightProperty())
                        }
                    } else {
                        scope?.let {
//                            find(MainView::class, it).removeFromParent()
                            mapOfScope.remove(connectionId)
                            it.deregister()
                        }
                    }
                }
            })
            appController.onStart()
        }
    }

}