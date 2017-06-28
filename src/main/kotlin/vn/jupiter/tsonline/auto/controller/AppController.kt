package vn.jupiter.tsonline.auto.controller

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.ByteToMessageDecoder
import io.netty.handler.logging.LogLevel
import io.reactivex.netty.protocol.tcp.client.TcpClient
import io.reactivex.netty.protocol.tcp.server.TcpServer
import javafx.beans.property.*
import javafx.collections.FXCollections
import javafx.collections.ObservableList
import org.jgrapht.alg.shortestpath.DijkstraShortestPath
import org.jgrapht.graph.DefaultDirectedWeightedGraph
import org.jgrapht.graph.DefaultWeightedEdge
import org.jgrapht.graph.DirectedWeightedMultigraph
import org.jgrapht.graph.WeightedMultigraph
import rx.subjects.PublishSubject
import tornadofx.*
import vn.jupiter.tsonline.auto.data.*
import vn.jupiter.tsonline.auto.utils.JavaFxScheduler
import java.io.File
import java.util.*
import kotlin.reflect.KClass
import kotlin.reflect.full.primaryConstructor

/**
 * Created by jupiter on 6/13/17.
 */

class PacketReceivedEvent(val packet: Packet) : FXEvent()

class PacketSentEvent(val packet: SendablePacket) : FXEvent()

class CharReadyEvent(val tsChar: TSChar)

data class TSCharItem(val id: Int)
//data class TS
data class TSChar(val id: LongProperty = SimpleLongProperty(0),
                  val name: StringProperty = SimpleStringProperty(""),
                  val level: IntegerProperty = SimpleIntegerProperty(0),
                  val mapId: IntegerProperty = SimpleIntegerProperty(0),
                  val x: IntegerProperty = SimpleIntegerProperty(0),
                  val y: IntegerProperty = SimpleIntegerProperty(0))


//data class TSWorld()
val receivedPacketRegistry = mapOf<Int, KClass<out Packet>>(
        0x0601 to PlayerWalkPacket::class,
        0x0C to PlayerAppearPacket::class,
        0x1408 to ActionOverPacket::class,
        0x1704 to ItemsInMapPacket::class,
        0x04 to PlayerShortInfoPacket::class,
        0x03 to PlayerOnlinePacket::class,
        0x1808 to PlayerUpdatePacket::class,
        0x0B0A to BattleStartedPacket::class,
        0x0B00 to BattleStopPacket::class
)

val sentPacketRegistry = mapOf<Int, KClass<out SendablePacket>>(
        0x00 to InitPacket::class,
        0x0108 to LoginPacket::class,
        0x4203 to OpenShop::class,
        0x0601 to WalkPacket::class,
        0x1408 to WarpPacket::class,
        0x1401 to ClickNPCPacket::class,
        0x1409 to ChooseMenuPacket::class,
        0x1406 to SendEndPacket::class,
        0x3201 to SendAttackPacket::class,
        0x0F05 to UnHorsePacket::class
)


class AppController : Controller() {
    val connectionMap = mutableMapOf<String, ObservableList<TSPacket>>()
    val targetServerIP = "121.201.42.128"
    val targetPort = 6414
    val proxyServerIP = "127.0.0.1"
    val proxyPort = 6414
    private lateinit var server: TcpServer<ByteBuf, ByteBuf>
    private val packetProcessor = CombinedTSPacketHandler()
    private val manualSendPacketPublisher = PublishSubject.create<SendablePacket>()
    private val packetStream = PublishSubject.create<Packet>()


    val mapList = FXCollections.observableMap<Int, MapData>(TreeMap())
    val mapGraph = DefaultDirectedWeightedGraph<Int, DefaultWeightedEdge>(DefaultWeightedEdge::class.java)
    val walkDirectionsQueue = ArrayDeque<MapDirection>()
    var targetWarpMapId: Int? = null
    val tsChar = TSChar()


    fun onStart() {
        packetProcessor.addPacketHandler(object : TSPacketHandler {
            override fun onPacketProcessed(packet: Packet): Boolean {
                when (packet) {
                    is SendablePacket -> fire(PacketSentEvent(packet))
                    else -> fire(PacketReceivedEvent(packet))
                }
                return false
            }
        })
        packetProcessor.addPacketHandler(object : TSPacketHandler {
            override fun onPacketProcessed(packet: Packet): Boolean {
                packetStream.onNext(packet)
                return false
            }
        })

        packetStream
                .onBackpressureBuffer(1000)
                .observeOn(JavaFxScheduler.getInstance())
                .subscribe({ packet ->
                    when (packet) {
                        is LoginPacket -> tsChar.id.set(packet.id)
                        is PlayerAppearPacket -> {
                            when {
                                (packet.playerId == tsChar.id.get()) -> {
                                    tsChar.mapId.set(packet.mapId)
                                    tsChar.x.set(packet.x)
                                    tsChar.y.set(packet.y)
                                    if (walkDirectionsQueue.isNotEmpty()) {
                                        val currentWarpStep = walkDirectionsQueue.poll()
                                        if (currentWarpStep.targetId == packet.mapId) {
                                            val warpStep = walkDirectionsQueue.peek()
                                            if (warpStep != null) {
                                                sendPacket(WarpPacket(warpStep.warpId))
                                            } else {
                                                //Warp finish?
                                            }
                                        } else {
                                            walkDirectionsQueue.clear()
                                            targetWarpMapId?.let {
                                                warpTo(it)
                                            }
                                        }
                                    } else {
                                        targetWarpMapId = null
                                    }
                                }
                            }
                        }
                    }
                })
        server = TcpServer.newServer(proxyPort)
                .addChannelHandlerLast<ByteBuf, ByteBuf>("Send packet decoder", { TSPacketDecoder(packetProcessor, sentPacketRegistry, true) })
                .start({ serverConn ->
                    println("Got new connection $serverConn ${Thread.currentThread()}")
                    val client = TcpClient.newClient(targetServerIP, targetPort)
                            .enableWireLogging("proxy-server_log", LogLevel.INFO)
                            .addChannelHandlerLast<ByteBuf, ByteBuf>("Receive packet decoder", { TSPacketDecoder(packetProcessor, receivedPacketRegistry) })
                    val connReq = client.createConnectionRequest()
                    serverConn.writeAndFlushOnEach(
                            connReq.flatMap { clientConn ->
                                clientConn
                                        .writeAndFlushOnEach(serverConn.input
                                                .mergeWith(manualSendPacketPublisher
                                                        .doOnNext {
                                                            println("Manually send $it")
                                                            packetProcessor.onPacketProcessed(it)
                                                        }
                                                        .map { it -> it.toBytePacket() })
                                                .doOnNext { it ->
                                                    if (it.hasArray()) {
                                                        println("Send ${it.array().toHex()}")
                                                    }
                                                }
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

    fun sendPacket(packet: SendablePacket) {
        println("Send $packet from ${Thread.currentThread()}")
        manualSendPacketPublisher.onNext(packet)
    }

    fun loadStaticData() {
        val warpFile = File("WarpID.ini")
        val warpDirectionRegExp = Regex("1\t([0-9]+)\t(\\d)\t([0-9]+)")
        val mapDataRegExp = Regex("2\t([0-9]+)\t(.*)")
        if (warpFile.exists()) {
            mapList.clear()
            warpFile.forEachLine { line ->
                when {
                    line.startsWith("1") -> {
                        warpDirectionRegExp.matchEntire(line)?.groupValues?.let {
                            val sourceId = Integer.parseInt(it[1])
                            val warpId = Integer.parseInt(it[2])
                            val targetId = Integer.parseInt(it[3])
                            mapGraph.addVertex(sourceId)
                            mapGraph.addVertex(targetId)
                            val edge = mapGraph.addEdge(sourceId, targetId)
                            if (edge != null) {
                                mapGraph.setEdgeWeight(edge, warpId.toDouble())
                            } else {
                                println("Cannot add edge between $sourceId $targetId $warpId ${mapGraph.getEdge(sourceId, targetId)}")
                            }
                        }
                    }
                    line.startsWith("2") -> {
                        mapDataRegExp.matchEntire(line)?.groupValues?.let {
                            val mapId = Integer.parseInt(it[1])
                            mapList[mapId] = MapData(mapId, it[2])
                        }
                    }
                }
            }
        }
    }

    fun warpTo(mapId: Int): ObservableList<WalkDirection> {
        targetWarpMapId = mapId
        val walkSteps = FXCollections.observableArrayList<WalkDirection>()
        if (walkDirectionsQueue.isNotEmpty()) {
            //TODO (Fix this, wait for warp to finish)
            walkDirectionsQueue.clear()
        } else {
            val shortestPathAlgo = DijkstraShortestPath<Int, DefaultWeightedEdge>(mapGraph)
            val path = shortestPathAlgo.getPath(tsChar.mapId.get(), mapId)
            if (path != null && path.length > 0) {
                val graph = path.graph
                path.edgeList.forEach {
                    val edgeWeight = graph.getEdgeWeight(it)
                    walkDirectionsQueue.add(MapDirection(graph.getEdgeSource(it), graph.getEdgeTarget(it), edgeWeight.toInt()))
                }
                val warpStep = walkDirectionsQueue.peek()
                sendPacket(WarpPacket(warpStep.warpId))
            }
        }
        return walkSteps
    }
}

interface TSPacketHandler {
    fun onPacketProcessed(packet: Packet): Boolean
}


class CombinedTSPacketHandler(var processorList: List<TSPacketHandler> = listOf<TSPacketHandler>()) : TSPacketHandler {
    override fun onPacketProcessed(packet: Packet): Boolean {
        return processorList.any {
            it.onPacketProcessed(packet)
        }
    }

    fun addPacketHandler(packetHandler: TSPacketHandler) {
        processorList += packetHandler
    }
}

class TSPacketDecoder(val tsPacketHandler: TSPacketHandler?, val registry: Map<Int, KClass<out Packet>> = emptyMap<Int, KClass<out Packet>>(),
                      val isDecodingSendPacket: Boolean = false) : ByteToMessageDecoder() {
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
            if (tsPacketHandler?.onPacketProcessed(tsPacket)?.not() ?: false) {
                out?.add(byteBuf)
            }
        })
    }

    private fun decodeBuffer(buffer: ByteBuf?, packetProcessor: (ByteBuf, Packet) -> Unit) {
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
                            val packet = splitPacket(decodedBuffer, packetSize)
                            val originalPackets = buffer.readBytes(packetSize + 4)
                            packetProcessor(originalPackets, packet)
//                            println("On mitm packet received $packet==${packetSize + 4} ${decodedBuffer.readableBytes()}/${buffer.readableBytes()}")
                        } else {
                            val remainingData = ByteArray(buffer.readableBytes())
                            buffer.getBytes(buffer.readerIndex(), remainingData)
//                            println("Packet is missing some part ${decodedBuffer.readableBytes()}/${buffer.readableBytes()} ${remainingData.toHex()}")
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

    fun splitPacket(byteBuf: ByteBuf, packetSize: Int): Packet {
        if (byteBuf.readableBytes() > 0) {
            val oldReaderIndex = byteBuf.readerIndex()
            val actionPrefix = byteBuf.readUnsignedByte().toInt()
            val actionSuffix = if (byteBuf.readableBytes() > 0) byteBuf.readUnsignedByte().toInt() else null
            val action = actionSuffix?.or(actionPrefix.shl(8)) ?: actionPrefix.shl(8)
            val tsPacket = when {
                registry.containsKey(action) -> {
                    val byteArray = ByteArray(maxOf(packetSize - 2, 0))
                    if (packetSize > 2) {
                        byteBuf.getBytes(oldReaderIndex + 2, byteArray)
                    }
                    with(registry[action]!!.primaryConstructor!!) {
                        if (parameters.isNotEmpty()) {
                            call(byteArray)
                        } else {
                            call()
                        }
                    }
                }
                registry.containsKey(actionPrefix) -> {
                    val byteArray = ByteArray(packetSize - 1)
                    byteBuf.getBytes(oldReaderIndex + 1, byteArray)
                    with(registry[actionPrefix]!!.primaryConstructor!!) {
                        if (parameters.isNotEmpty()) {
                            call(byteArray)
                        } else {
                            call()
                        }
                    }
                }
                else -> {
                    val byteArray = ByteArray(packetSize)
                    byteBuf.getBytes(oldReaderIndex, byteArray)
                    if (isDecodingSendPacket) {
                        RawSendablePacket(byteArray)
                    } else {
                        RawPacket(byteArray)
                    }
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


