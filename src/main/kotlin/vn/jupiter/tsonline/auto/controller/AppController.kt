package vn.jupiter.tsonline.auto.controller

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.ByteToMessageDecoder
import io.netty.handler.logging.LogLevel
import io.reactivex.netty.protocol.tcp.client.TcpClient
import io.reactivex.netty.protocol.tcp.server.TcpServer
import javafx.collections.ObservableList
import rx.subjects.PublishSubject
import tornadofx.*
import vn.jupiter.tsonline.auto.data.*

/**
 * Created by jupiter on 6/13/17.
 */

class PacketReceivedEvent(val packet: TSPacket) : FXEvent()

class PacketSentEvent(val packet: TSPacket) : FXEvent()

class CharReadyEvent(val tsChar: TSChar)

data class TSChar(val ID: String, val name: String)

class AppController : Controller() {
    val connectionMap = mutableMapOf<String, ObservableList<TSPacket>>()
    val targetServerIP = "121.201.42.128"
    val targetPort = 6414
    val proxyServerIP = "127.0.0.1"
    val proxyPort = 6414
    private lateinit var server: TcpServer<ByteBuf, ByteBuf>
    private val packetProcessor = CombinedTSPacketHandler()
    private val manualSendPacketPublisher = PublishSubject.create<TSPacket>()

    fun onStart() {
        packetProcessor.addPacketHandler(object : TSPacketHandler() {
            override fun onPacketReceived(packet: TSPacket): Boolean {
                fire(PacketReceivedEvent(packet))
                return false
            }

            override fun onPacketSent(packet: TSPacket): Boolean {
                fire(PacketSentEvent(packet))
                return false
            }
        })
        server = TcpServer.newServer(proxyPort)
                .enableWireLogging("proxy-server_log", LogLevel.INFO)
                .addChannelHandlerLast<ByteBuf, ByteBuf>("Send packet decoder", { TSPacketDecoder(packetProcessor, true) })
                .start({ serverConn ->
                    println("Got new connection $serverConn ${Thread.currentThread()}")
                    val client = TcpClient.newClient(targetServerIP, targetPort)
                            .addChannelHandlerLast<ByteBuf, ByteBuf>("Receive packet decoder", { TSPacketDecoder(packetProcessor) })
                    val connReq = client.createConnectionRequest()
                    serverConn.writeAndFlushOnEach(
                            connReq.flatMap { clientConn ->
                                clientConn
                                        .writeAndFlushOnEach(serverConn.input
                                                .mergeWith(manualSendPacketPublisher
                                                        .map { it -> it.toBytePacket() })
                                        )
                                        .cast(ByteBuf::class.java)
                                        .mergeWith(clientConn.input)
                            }
                    )

                })
    }

    fun cleanUp() {
        server.shutdown()
    }

    fun sendPacket(packet: TSPacket) {
        manualSendPacketPublisher.onNext(packet)
        fire(PacketSentEvent(packet))
    }
}

abstract class TSPacketHandler {
    open var ctx: ChannelHandlerContext? = null
    abstract fun onPacketReceived(packet: TSPacket): Boolean
    abstract fun onPacketSent(packet: TSPacket): Boolean
}


class CombinedTSPacketHandler(var processorList: List<TSPacketHandler> = listOf<TSPacketHandler>()) : TSPacketHandler() {
    override var ctx: ChannelHandlerContext? = null
        set(value) {
            field = value
            for (tsPacketHandler in processorList) {
                tsPacketHandler.ctx = value
            }
        }

    override fun onPacketReceived(tsPacket: TSPacket): Boolean {
        return processorList.any { it.onPacketReceived(tsPacket) }
    }

    override fun onPacketSent(tsPacket: TSPacket): Boolean {
        return processorList.any { it.onPacketSent(tsPacket) }
    }

    fun addPacketHandler(packetHandler: TSPacketHandler) {
        processorList += packetHandler
    }
}

class TSPacketDecoder(val tsPacketHandler: TSPacketHandler?, val isToTargetServer: Boolean = false) : ByteToMessageDecoder() {
    override fun channelActive(ctx: ChannelHandlerContext?) {
        super.channelActive(ctx)
//        tsPacketHandler?.ctx = ctx
    }

    override fun channelInactive(ctx: ChannelHandlerContext?) {
        super.channelInactive(ctx)
//        tsPacketHandler?.ctx = null
    }

    override fun decode(ctx: ChannelHandlerContext?, buffer: ByteBuf?, out: MutableList<Any>?) {
        decodeBuffer(buffer, { byteBuf, tsPacket ->
            if (isToTargetServer) {
                if (tsPacketHandler?.onPacketSent(tsPacket)?.not() ?: false) {
                    out?.add(byteBuf)
                }
            } else {
                if (tsPacketHandler?.onPacketReceived(tsPacket)?.not() ?: false) {
                    out?.add(byteBuf)
                }
            }
        })
    }

    private fun decodeBuffer(buffer: ByteBuf?, packetProcessor: (ByteBuf, TSPacket) -> Unit) {
        buffer?.let {
            try {
                val messageSize = buffer.readableBytes()
                val decodedBuffer = Unpooled.buffer(messageSize, messageSize)
                val oldReaderIndex = buffer.readerIndex()
                val byteArray = ByteArray(buffer.readableBytes())
                buffer.getBytes(buffer.readerIndex(), byteArray)
                while (buffer.isReadable) {
                    val readByte = buffer.readUnsignedByte()
                    decodedBuffer.writeByte(readByte.toInt().xor(0xAD))
                }
                buffer.readerIndex(oldReaderIndex)
                decodedBuffer.resetReaderIndex()
                while (decodedBuffer.readableBytes() >= 4) {
                    val headerByte = decodedBuffer.readUnsignedByte().toInt()
                    val nextHeaderByte = decodedBuffer.readUnsignedByte().toInt()
                    if (headerByte == 0xF4 && nextHeaderByte == 0x44) {
                        val packetSize = decodedBuffer.readShortLE().toInt()
                        if (packetSize <= decodedBuffer.readableBytes()) {
                            val packet = splitPacket(decodedBuffer, packetSize, !isToTargetServer)
                            val originalPackets = buffer.readBytes(packetSize + 4)
                            packetProcessor(originalPackets, packet)
                            println("On mitm packet received $packet==${packetSize + 4} ${decodedBuffer.readableBytes()}/${buffer.readableBytes()}")
                        } else {
                            val remainingData = ByteArray(buffer.readableBytes())
                            buffer.getBytes(buffer.readerIndex(), remainingData)
                            println("Packet is missing some part ${decodedBuffer.readableBytes()}/${buffer.readableBytes()} ${remainingData.toHex()}")
                            break
                        }
                    } else {
                        println("Wrong header at ${decodedBuffer.readerIndex()} ${decodedBuffer.array().toHex()}")
                        break
                    }
                }
                decodedBuffer.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    companion object {
        val receivePacketRegistry = mapOf<Int, ((ByteArray) -> TSPacket)>(
                0x00 to { _ -> InitPacket() },
                0x0108 to { array -> LoginPacket(array) },
                0x0601 to { array -> PlayerWalkPacket(array) },
                0x1401 to { array -> ClickNPCPacket(array) }
        )
        val sendPacketRegistry = receivePacketRegistry.toMutableMap {
            arrayOf<Pair<Int, ((ByteArray) -> TSPacket)>>(
                    0x4203 to { _ -> OpenShop() },
                    0x0601 to { array -> WalkPacket(array) },
                    0x1408 to { array -> WarpPacket(array) }

            )

        }

    }

    fun splitPacket(byteBuf: ByteBuf, packetSize: Int, isReceivingPacket: Boolean): TSPacket {
        if (byteBuf.readableBytes() > 0) {
            val oldReaderIndex = byteBuf.readerIndex()
            val actionPrefix = byteBuf.readUnsignedByte().toInt()
            val actionSuffix = if (byteBuf.readableBytes() > 0) byteBuf.readUnsignedByte().toInt() else null
            val action = actionSuffix?.or(actionPrefix.shl(8)) ?: actionPrefix.shl(8)

            val packetRegistry = if (isReceivingPacket) receivePacketRegistry else sendPacketRegistry

            val tsPacket = when {
                packetRegistry.containsKey(action) -> {
                    val byteArray = ByteArray(maxOf(packetSize - 2, 0))
                    if (packetSize > 2) {
                        byteBuf.getBytes(oldReaderIndex + 2, byteArray)
                    }
                    packetRegistry[action]!!.invoke(byteArray)
                }
                packetRegistry.containsKey(actionPrefix) -> {
                    val byteArray = ByteArray(packetSize - 1)
                    byteBuf.getBytes(oldReaderIndex + 1, byteArray)
                    packetRegistry[actionPrefix]!!.invoke(byteArray)
                }
                else -> {
                    val byteArray = ByteArray(packetSize)
                    byteBuf.getBytes(oldReaderIndex, byteArray)
                    UnProcessedPacket(byteArray)
                }
            }
            byteBuf.readerIndex(oldReaderIndex + packetSize)
            return tsPacket
        } else {
            println("Packet has no data")
            throw error("Packet has no data")
        }
    }
}

private fun <K, V> Map<K, V>.toMutableMap(init: () -> Array<Pair<K, V>>): MutableMap<K, V> {
    val newMap = toMutableMap()
    newMap.putAll(init())
    return newMap
}


