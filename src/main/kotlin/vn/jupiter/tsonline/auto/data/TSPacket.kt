package vn.jupiter.tsonline.auto.data

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled

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

sealed class TSPacket(val command: Int?, byteArray: ByteArray = ByteArray(0)) {
    val byteBuffer = Unpooled.copiedBuffer(byteArray)

    fun toBytePacket(): ByteBuf {
        val commandLength = when {
            command == null -> 0
            command > 0xFF -> 2
            else -> 1
        }
        val packetLength = byteBuffer.capacity() + commandLength
        val packet = Unpooled.buffer(packetLength + 4, packetLength + 4)
        packet.writeByte(0x59)
        packet.writeByte(0xE9)
        packet.writeShortLE(packetLength.xor(0xADAD))
        command?.let {
            if (commandLength == 2) {
                packet.writeShortLE(command.xor(0xADAD))
            } else {
                packet.writeByte(command.xor(0xAD))
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

    override fun toString(): String {
        return javaClass.simpleName + " " + byteBuffer.array().toHex()
    }
}

class UnProcessedPacket(byteArray: ByteArray) : TSPacket(null, byteArray)

class InitPacket : TSPacket(0x0, ByteArray(0))

class LoginPacket(byteArray: ByteArray) : TSPacket(0x01, byteArray) {
    val id: Int
        get() = byteBuffer.getIntLE(0)
    val password: String
        get() = byteBuffer.getCharSequence(8, byteBuffer.capacity() - 8, Charsets.US_ASCII).toString()

    constructor(id: Int, password: String, version: Int = 188, prefix: String = "WP")
            : this(kotlin.ByteArray(4 + 2 + 2 + password.length)) {
        byteBuffer.writeIntLE(id)
        byteBuffer.writeCharSequence(prefix, Charsets.US_ASCII)
        byteBuffer.writeShortLE(version)
        byteBuffer.writeCharSequence(password, Charsets.US_ASCII)
    }

    override fun toString(): String {
        return "${javaClass.simpleName} ${id} ${password}"
    }
}

//0x4203
class OpenShop() : TSPacket(0x4203, ByteArray(0))

class ClickNPCPacket(byteArray: ByteArray) : TSPacket(0x1401, byteArray) {
    val npcId: Int
        get() = byteBuffer.getShortLE(0).toInt()

    override fun toString(): String {
        return "Click NPC $npcId"
    }
}


class WarpPacket(byteArray: ByteArray) : TSPacket(0x1408, byteArray) {
    val warpId: Int
        get() = byteBuffer.getUnsignedShortLE(0)

    constructor(warpId: Int) : this(ByteArray(2)) {
        byteBuffer.writeShortLE(warpId)
    }

    override fun toString(): String {
        return "Warp via $warpId"
    }

}

class PlayerAppearPacket(byteArray: ByteArray) : TSPacket(0x0C, byteArray) {
    val playerId: Int
        get() = byteBuffer.getIntLE(0)
    val mapId: Short
        get() = byteBuffer.getShortLE(4)
    val x: Short
        get() = byteBuffer.getShortLE(6)
    val y: Short
        get() = byteBuffer.getShortLE(8)


}

//Receive 0x0601
class PlayerWalkPacket(byteArray: ByteArray) : TSPacket(0x0601, byteArray) {


}

//Send 0x0601
enum class WalkDirection(val value: Byte) {
    DOWN(0x01),
    UP(0x00),
    LEFT(0x02),
    RIGHT(0x06),
    LTOP(0x01),
    RTOP(0x07),
    LBOT(0x03),
    RBOT(0x05);

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

class WalkPacket(byteArray: ByteArray) : TSPacket(0x0601, byteArray) {
    val direction: WalkDirection
        get() = WalkDirection.fromByte(byteBuffer.getByte(0))
    val x: Int
        get() = byteBuffer.getUnsignedShortLE(1)
    val y: Int
        get() = byteBuffer.getUnsignedShortLE(3)

    constructor(direction: WalkDirection, x: Int, y: Int) : this(ByteArray(7)) {
        byteBuffer.writeByte(direction.value.toInt())
        byteBuffer.writeShortLE(x)
        byteBuffer.writeShortLE(y)
        byteBuffer.writeByte(0x90)
        byteBuffer.writeByte(0x57)
    }

    override fun toString(): String {
        return "Walk to $x $y $direction"
    }
}

//0x0C
class WarpSuccessPacket()

//0x0B0A
class BattleRelatedPacket()

//0x1704
class ItemInMapPacket()

//0x0B02
class RequestObservePacket()


