package vn.jupiter.tsonline.auto.data

import org.jgrapht.GraphPath

/**
 * Created by jupiter on 6/27/17.
 */
data class MapData(val id: Int, val name: String?) {
    override fun toString(): String {
        return "$id - $name"
    }
}

sealed class GameEvent

data class PathCalculated(val path: GraphPath<*,*>?): GameEvent()
class WarpingStarted : GameEvent()
class WarpingEnded : GameEvent()
data class MapDirection(val fromId: Int, val targetId: Int, val warpId: Int) : GameEvent()

class BattleStarted : GameEvent()
class BattleEnded : GameEvent()
data class NpcClicked(val npcId: Int) : GameEvent()
data class MenuChosen(val choiceId: Int) : GameEvent()
data class DialogAppear(val dialogId: Int, val dialogType: Byte) : GameEvent()
class NpcAppeared : GameEvent()
data class ItemAppeared(val items: List<ItemInMap>) : GameEvent()
data class ItemReceived(val itemId: Int): GameEvent()
class MenuAppear : GameEvent()
class TalkFinished : GameEvent()
data class WalkFinished(val x: Int, val y: Int) : GameEvent()
data class ItemPicked(val itemId: Int): GameEvent()
data class MapChanged(val sourceMapId: Int, val targetMapId: Int): GameEvent()
class WarpSameMapFinished : GameEvent()

