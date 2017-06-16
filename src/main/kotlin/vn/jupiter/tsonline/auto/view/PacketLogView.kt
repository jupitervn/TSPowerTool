package vn.jupiter.tsonline.auto.view

import javafx.scene.Parent
import javafx.scene.control.ListView
import tornadofx.*
import vn.jupiter.tsonline.auto.app.Styles
import vn.jupiter.tsonline.auto.data.TSPacket

/**
 * Created by jupiter on 6/13/17.
 */
class PacketLogView : View() {
    override val root =  vbox {
        label("Log") {
            addClass(Styles.heading)
        }

        listview<String> {

        }
    }

    val listPackets: ListView<TSPacket> by singleAssign()

    init {

    }

}
