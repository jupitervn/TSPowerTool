package vn.jupiter.tsonline.auto.app

import javafx.collections.FXCollections
import org.jgrapht.graph.DefaultDirectedWeightedGraph
import org.jgrapht.graph.DefaultWeightedEdge
import tornadofx.*
import vn.jupiter.tsonline.auto.controller.TSFunction
import vn.jupiter.tsonline.auto.data.MapData
import java.util.*


/**
 * Created by jupiter on 7/2/17.
 */
class ConnectionScope(val tsFunction: TSFunction) : Scope() {
}