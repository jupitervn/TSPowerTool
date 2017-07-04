package vn.jupiter.tsonline.auto.data

/**
 * Created by jupiter on 7/2/17.
 */
data class Quest(val name: String, val steps: List<QuestStep> = mutableListOf())

sealed class QuestStep {
    abstract val customName: String?
    abstract val x: Int?
    abstract val y: Int?
    abstract val mapId: Int
    abstract val choiceId: Int?
}

data class TalkToNpc(override val mapId: Int, override val x: Int? = null, override val y: Int? = null, val npcId: Int, override val choiceId: Int? = null, override val customName: String = "Talk with $npcId")
    : QuestStep()
data class WarpToId(override val mapId: Int, override val x: Int? = null, override val y: Int? = null, val warpId: Int, override val choiceId: Int? = null, override val customName: String = "Warp through $warpId")
    : QuestStep()
data class SpecialWarpToId(override val mapId: Int, override val x: Int? = null, override val y: Int? = null, val warpId: Int, override val choiceId: Int? = null, override val customName: String = "Special Warp through $warpId")
    : QuestStep()
data class PickItemWithId(override val mapId: Int, override val x: Int? = null, override val y: Int? = null, val itemId: Int, override val customName: String = "Pick item $itemId")
    : QuestStep() {
    override val choiceId: Int? = null
}
