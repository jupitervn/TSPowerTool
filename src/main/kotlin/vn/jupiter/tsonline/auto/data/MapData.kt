package vn.jupiter.tsonline.auto.data

/**
 * Created by jupiter on 6/27/17.
 */
data class MapData(val id: Int, val name: String?) {
    override fun toString(): String {
        return "$id - $name"
    }
}

data class MapDirection(val fromId: Int, val targetId: Int, val warpId: Int)