package vn.jupiter.tsonline.auto.view

import javafx.geometry.Pos
import javafx.scene.control.ToggleGroup
import javafx.util.StringConverter
import javafx.util.converter.NumberStringConverter
import tornadofx.*
import vn.jupiter.tsonline.auto.app.Styles
import vn.jupiter.tsonline.auto.controller.MainController
import javax.swing.GroupLayout

class MainView : View("") {
    val controller: MainController by inject(scope)
    val autoQuestView = find(AutoQuestTabView::class, scope)
    var packetLogView = find(PacketLogView::class)
    val warpView = find(WarpView::class, scope)
    override val root = borderpane {
        top {
            label("") {
                addClass(Styles.heading)
            }
        }

        center {
            tabpane {
                tab("AutoQuest", autoQuestView.root)
                tab("Warp", warpView.root)
                tab("Environment")
            }
        }

        left {
            add(packetLogView)
            minWidth = 200.0
        }

        bottom {
            hbox {
                spacing = 10.0
                label("Mapname") {
                    bind(controller.mapNameProperty)
                    alignment = Pos.CENTER_RIGHT
                }
                label("MapId") {
                    textProperty().bindBidirectional(controller.tsFunction.tsChar.mapId, NumberStringConverter())
                    alignment = Pos.CENTER_RIGHT
                }

                togglebutton(">", group = ToggleGroup()) {
                    action {
                        if (left.isVisible) {
                            left.hide()
                        } else {
                            left.show()
                        }
                    }
                }

            }
        }


    }

    init {
        root.left.hide()
    }

    override fun onUndock() {
        super.onUndock()
    }
}