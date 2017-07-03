package vn.jupiter.tsonline.auto.data

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import vn.jupiter.tsonline.auto.controller.TSCharItem
import java.nio.charset.Charset
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

/**
 * Created by jupiter on 6/13/17.
 */

private val HEX_CHARS = "0123456789ABCDEF".toCharArray()

fun ByteArray.toHex(): String {
    val result = StringBuffer()

    forEachIndexed { index, byte ->
        val octet = byte.toInt()
        val firstIndex = (octet and 0xF0).ushr(4)
        val secondIndex = octet and 0x0F
//        result.append("$$index ")
        result.append(HEX_CHARS[firstIndex])
        result.append(HEX_CHARS[secondIndex])
        result.append(" ")
        if (index > 0 && index % 16 == 0) {
            result.append("\n")
        }
//        if ((index + 1) % 2 == 0) {
//            result.append(" ")
//        }
    }

    return result.toString()
}

val defaultCharset = Charset.forName("Big5")

enum class Ele(val value: Byte) {
    WIND(0x1),
    EARTH(0x2),
    FIRE(0x3),
    WATER(0x4),
    NONE(0x0);


    companion object {
        fun fromByte(byte: Byte): Ele {
            Ele.values().forEach {
                if (it.value == byte) {
                    return it
                }
            }
            return NONE
        }

    }
}

enum class RebornState(val value: Byte) {
    NOT_EVO(0x0),
    EVO(0x1),
    REVO(0x2);

    companion object {
        fun fromByte(byte: Byte): RebornState {
            RebornState.values().forEach {
                if (it.value == byte) {
                    return it
                }
            }
            return NOT_EVO
        }

    }

}

data class TSCharacterInfo(val id: Long, val name: String, val level: Short, val element: Ele, val reborn: RebornState,
                           val items: Array<TSCharItem> = emptyArray<TSCharItem>())

sealed class Packet(val command: Int?, byteArray: ByteArray = ByteArray(0)) {
    val byteBuffer: ByteBuf = Unpooled.copiedBuffer(byteArray)!!

    override fun toString(): String {
        return javaClass.simpleName + " " + packetDesc()
    }

    fun getRawIntLE(index: Int): Long {
        return if (index <= byteBuffer.writerIndex() - 4) {
            byteBuffer.getUnsignedIntLE(index)
        } else {
            0
        }
    }

    fun getRawShortLE(index: Int): Int {
        return if (index <= byteBuffer.writerIndex() - 2) {
            byteBuffer.getUnsignedShortLE(index)
        } else {
            0
        }
    }

    fun getRawByte(index: Int): Short {
        return if (index <= byteBuffer.writerIndex() - 1) {
            byteBuffer.getUnsignedByte(index)
        } else {
            0
        }
    }

    fun getIntLE(index: Int): ReadOnlyProperty<Packet, Long> {
        return object : ReadOnlyProperty<Packet, Long> {
            override fun getValue(thisRef: Packet, property: KProperty<*>): Long {
                if (index + 4 <= byteBuffer.writerIndex()) {
                    return thisRef.byteBuffer.getUnsignedIntLE(index)
                } else {
                    return 0
                }
            }
        }
    }

    fun getShortLE(index: Int): ReadOnlyProperty<Packet, Int> {
        return object : ReadOnlyProperty<Packet, Int> {
            override fun getValue(thisRef: Packet, property: KProperty<*>): Int {
                if (index + 2 <= byteBuffer.writerIndex()) {
                    return thisRef.byteBuffer.getUnsignedShortLE(index)
                } else {
                    return 0
                }
            }
        }
    }

    fun getByte(index: Int): ReadOnlyProperty<Packet, Byte> {
        return object : ReadOnlyProperty<Packet, Byte> {
            override fun getValue(thisRef: Packet, property: KProperty<*>): Byte {
                if (index + 1 <= byteBuffer.writerIndex()) {
                    return thisRef.byteBuffer.getByte(index)
                } else {
                    return 0
                }
            }
        }
    }

    fun getUnsignedByte(index: Int): ReadOnlyProperty<Packet, Short> {
        return object : ReadOnlyProperty<Packet, Short> {
            override fun getValue(thisRef: Packet, property: KProperty<*>): Short {
                if (index + 1 <= byteBuffer.writerIndex()) {
                    return thisRef.byteBuffer.getUnsignedByte(index)
                } else {
                    return 0
                }
            }
        }
    }

    fun getString(index: Int, length: Int): ReadOnlyProperty<Packet, String> {
        return object : ReadOnlyProperty<Packet, String> {
            override fun getValue(thisRef: Packet, property: KProperty<*>): String {
                if (index + length <= byteBuffer.writerIndex()) {
                    return thisRef.byteBuffer.getCharSequence(index, length, Charset.forName("Big5")).toString()
                } else {
                    return "Cannot read string $index $length"
                }
            }
        }
    }

    open fun packetDesc(): String {
        return byteBuffer.array().toHex()
    }

    fun toBytePacket(): ByteBuf {
        val commandLength = when {
            command == null -> 0
            command!! > 0xFF -> 2
            else -> 1
        }
        val packetLength = byteBuffer.capacity() + commandLength
        val packet = Unpooled.buffer(packetLength + 4, packetLength + 4)
        packet.writeByte(0x59)
        packet.writeByte(0xE9)
        packet.writeShortLE(packetLength.xor(0xADAD))
        command?.let {
            if (commandLength == 2) {
                packet.writeShort(it.xor(0xADAD))
            } else {
                packet.writeByte(it.xor(0xAD))
            }
        }
        byteBuffer.resetReaderIndex()
        while (byteBuffer.isReadable) {
            val byteToWrite = byteBuffer.readByte().toInt().xor(0xAD)
            packet.writeByte(byteToWrite)
        }
        byteBuffer.resetReaderIndex()
        return packet
    }
}

sealed class SendablePacket(command: Int?, byteArray: ByteArray = ByteArray(0)) : Packet(command, byteArray)

class RawSendablePacket(byteArray: ByteArray) : SendablePacket(null, byteArray)
class RawPacket(byteArray: ByteArray) : Packet(null, byteArray)

class InitPacket : SendablePacket(0x0, ByteArray(0))

class LoginPacket(byteArray: ByteArray) : SendablePacket(0x0108, byteArray) {
    val id by getIntLE(0)
    val password: String by getString(8, byteBuffer.writerIndex() - 8)

    constructor(id: Int, password: String, version: Int = 188, prefix: String = "WP")
            : this(kotlin.ByteArray(4 + 2 + 2 + password.length)) {
        byteBuffer.writeIntLE(id)
        byteBuffer.writeCharSequence(prefix, Charsets.US_ASCII)
        byteBuffer.writeShortLE(version)
        byteBuffer.writeCharSequence(password, Charsets.US_ASCII)
    }

    override fun packetDesc(): String {
        return "${id} ${password}"
    }
}

//0x4203
class OpenShop : SendablePacket(0x4203)

class ClickNPCPacket(byteArray: ByteArray) : SendablePacket(0x1401, byteArray) {
    val npcId by getShortLE(0)

    constructor(npdId: Int) : this(kotlin.ByteArray(2)) {
        byteBuffer.setShortLE(0, npdId)
    }

    override fun packetDesc(): String {
        return "$npcId"
    }
}

class ChooseMenuPacket(byteArray: ByteArray) : SendablePacket(0x1409, byteArray) {
    val menuId: Short = (byteBuffer.getUnsignedByte(0) - 29).toShort()

    constructor(choiceId: Int) : this(kotlin.ByteArray(1)) {
        byteBuffer.setByte(0, choiceId + 29)
    }

    override fun packetDesc(): String {
        return "$menuId"
    }
}

class NpcDialogPacket(byteArray: ByteArray) : Packet(0x1401, byteArray) {
    val type by getByte(4)
    val dialogId by getShortLE(13)

    override fun packetDesc(): String {
        return "DialogId: $dialogId $type"
    }
}

class SendEndPacket : SendablePacket(0x1406)

class ActionOverPacket : Packet(0x1408)

class UnHorsePacket : SendablePacket(0x0F05)

//0F 04 53 46 00 00
class HorsePacket : SendablePacket(0x0F04)


class WarpPacket(byteArray: ByteArray) : SendablePacket(0x1408, byteArray) {
    val warpId by getShortLE(0)

    constructor(warpId: Int) : this(ByteArray(2)) {
        byteBuffer.resetWriterIndex()
        byteBuffer.writeShortLE(warpId)
    }

    override fun packetDesc(): String {
        return "Warp via $warpId"
    }

}

class PlayerAppearPacket(byteArray: ByteArray) : Packet(0x0C, byteArray) {
    val playerId by getIntLE(0)
    val mapId by getShortLE(4)
    val x by getShortLE(6)
    val y by getShortLE(8)

    override fun packetDesc(): String {
        return "$playerId at $mapId $x $y"
    }
}

//Receive 0x0601
class PlayerWalkPacket(byteArray: ByteArray) : Packet(0x0601, byteArray) {
    val partyId by getIntLE(0)
    val x by getShortLE(4)
    val y by getShortLE(6)

    constructor(playerId: Long, x: Int, y: Int) : this(kotlin.ByteArray(9)) {
        byteBuffer.setIntLE(0, playerId.toInt())
        byteBuffer.setShortLE(4, x)
        byteBuffer.setShortLE(6, y)
        byteBuffer.setByte(8, 0x1)
    }

    override fun packetDesc(): String {
        return "$partyId $x $y"
    }

}

//Send 0x0601
enum class WalkDirection(val value: Byte) {
    DOWN(0x04),
    UP(0x00),
    LEFT(0x02),
    RIGHT(0x06),
    LTOP(0x03),
    RTOP(0x05),
    LBOT(0x01),
    RBOT(0x07);

    companion object {
        fun fromByte(byte: Byte): WalkDirection {
            values().forEach {
                if (it.value == byte) {
                    return it
                }
            }
            return DOWN
        }

    }
}

class WalkPacket(byteArray: ByteArray) : SendablePacket(0x0601, byteArray) {
    val direction: WalkDirection
        get() = WalkDirection.fromByte(byteBuffer.getByte(0))
    val x: Int by getShortLE(1)
    val y: Int by getShortLE(3)

    constructor(direction: WalkDirection, x: Int, y: Int) : this(ByteArray(7)) {
        byteBuffer.setByte(0, direction.value.toInt())
        byteBuffer.setShortLE(1, x)
        byteBuffer.setShortLE(3, y)
        byteBuffer.setByte(5, 0x9A)
        byteBuffer.setByte(6, 0x85)
    }

    override fun packetDesc(): String {
        return "$x $y $direction"
    }
}

data class ItemInMap(val itemType: Byte, val indexInMap: Int, val itemId: Int, val x: Int, val y: Int)

class ItemsInMapPacket(array: ByteArray) : Packet(0x1704, array) {
    val items = mutableListOf<ItemInMap>()

    init {
        while (byteBuffer.isReadable) {
            val itemType = byteBuffer.readByte()
            val itemIdx = byteBuffer.readUnsignedShortLE()
            val itemId = byteBuffer.readUnsignedShortLE()
            byteBuffer.skipBytes(2)
            val x = byteBuffer.readUnsignedShortLE()
            val y = byteBuffer.readUnsignedShortLE()
            items += ItemInMap(itemType, itemIdx, itemId, x, y)
        }
        byteBuffer.resetReaderIndex()
    }

    override fun packetDesc(): String {
        return "$items"
    }
}

class NpcInMapPacket(array: ByteArray) : Packet(0x1604, array) {

}

class NpcAppear(array: ByteArray) : Packet(0x1602, array) {

}

class PlayerShortInfoPacket(array: ByteArray) : Packet(0x04, array) {
    val tsCharacterInfo: TSCharacterInfo

    init {
        val playerId = byteBuffer.getUnsignedInt(0)
        val ele: Byte = byteBuffer.getByte(5)
        val level: Short = byteBuffer.getUnsignedByte(6)
        val mapId: Int = byteBuffer.getUnsignedShortLE(9)
        val x: Int = byteBuffer.getUnsignedShortLE(11)
        val y: Int = byteBuffer.getUnsignedShortLE(13)
        val noOfEquip = byteBuffer.getByte(26)
        val reborn = byteBuffer.getByte(26 + noOfEquip * 2 + 6)
        val name = byteBuffer.getCharSequence(26 + noOfEquip * 2 + 8, array.size - 26 - noOfEquip * 2 - 8, defaultCharset).toString()
        tsCharacterInfo = TSCharacterInfo(playerId, name, level, Ele.fromByte(ele), RebornState.fromByte(reborn))
    }

    override fun packetDesc(): String {
        return "$tsCharacterInfo"
    }
}

class PlayerOnlinePacket(array: ByteArray) : Packet(0x03, array) {
    val playerId by getIntLE(0)

    init {

    }

    override fun packetDesc(): String {
        return "$playerId"
    }
}


class PlayerUpdatePacket(array: ByteArray) : Packet(0x1808, array) {
    val playerId by getIntLE(0)


    override fun packetDesc(): String {
        return "$playerId"
    }
}

data class BattlePos(val row: Byte, val col: Byte)

class BattleStartedPacket(array: ByteArray) : Packet(0x0B0A, array)
class BattleStopPacket(array: ByteArray) : Packet(0x0B00, array) {
    val battleUid: Long by getIntLE(0)
}

class SendAttackPacket(array: ByteArray) : SendablePacket(0x3201, array) {

    constructor(sourcePos: BattlePos, targetPos: BattlePos, skillID: Int) : this(kotlin.ByteArray(8)) {
        byteBuffer.writeByte(sourcePos.row.toInt())
        byteBuffer.writeByte(sourcePos.col.toInt())
        byteBuffer.writeByte(targetPos.row.toInt())
        byteBuffer.writeByte(targetPos.col.toInt())
        byteBuffer.writeShortLE(skillID)
        byteBuffer.writeShort(0x0AA0)
    }

    val sourcePos: BattlePos
        get() = BattlePos(byteBuffer.getByte(0), byteBuffer.getByte(1))
    val targetPos: BattlePos
        get() = BattlePos(byteBuffer.getByte(2), byteBuffer.getByte(3))
    val skillID: Int
        get() = byteBuffer.getUnsignedShortLE(4)

    override fun packetDesc(): String {
        return "$sourcePos cast $skillID on $targetPos"
    }
}

//0x0B02
class RequestObservePacket()

class PickItemPacket(array: ByteArray) : SendablePacket(0x1702, array) {
    val itemIndex by getShortLE(0)

    constructor(itemIndex: Int) : this(kotlin.ByteArray(2)) {
        byteBuffer.setShortLE(0, itemIndex)
    }
}

class ItemReceivedPacket(array: ByteArray) : Packet(0x1706, array) {
    val itemId by getShortLE(0)
}

class BagItemReceivedPacket(array: ByteArray) : Packet(0x1730, array) {

}

class MapDisplayedOverPacket : Packet(0x0504)
class WarpSuccessPacket : Packet(0x1407)

class WarpFinishedAckPacket : SendablePacket(0x0C01)


