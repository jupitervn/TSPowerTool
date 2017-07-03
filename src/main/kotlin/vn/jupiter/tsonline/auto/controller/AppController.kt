package vn.jupiter.tsonline.auto.controller

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.handler.codec.ByteToMessageDecoder
import io.netty.util.ResourceLeakDetector
import io.reactivex.netty.channel.Connection
import io.reactivex.netty.client.ConnectionRequest
import io.reactivex.netty.protocol.tcp.client.TcpClient
import io.reactivex.netty.protocol.tcp.server.TcpServer
import javafx.beans.property.*
import javafx.collections.FXCollections
import javafx.collections.ObservableMap
import org.jgrapht.alg.shortestpath.DijkstraShortestPath
import org.jgrapht.graph.DefaultDirectedWeightedGraph
import org.jgrapht.graph.DefaultWeightedEdge
import rx.Observable
import rx.functions.Func1
import rx.schedulers.Schedulers
import rx.subjects.PublishSubject
import tornadofx.*
import vn.jupiter.tsonline.auto.data.*
import vn.jupiter.tsonline.auto.utils.JavaFxScheduler
import java.io.File
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.reflect.KClass
import kotlin.reflect.full.primaryConstructor

/**
 * Created by jupiter on 6/13/17.
 */

class PacketReceivedEvent(val packet: Packet) : FXEvent()

class PacketSentEvent(val packet: SendablePacket) : FXEvent()

class CharReadyEvent(val tsChar: TSChar)


data class TSCharItem(val id: Int)
enum class CharacterStatus {
    IDLE,
    BATTLING,
    WARPING;
}

//data class TS
data class TSChar(val id: LongProperty = SimpleLongProperty(0),
                  val name: StringProperty = SimpleStringProperty(""),
                  val level: IntegerProperty = SimpleIntegerProperty(0),
                  val mapId: IntegerProperty = SimpleIntegerProperty(0),
                  val x: IntegerProperty = SimpleIntegerProperty(0),
                  val y: IntegerProperty = SimpleIntegerProperty(0),
                  val inventory: MapProperty<Int, Int> = SimpleMapProperty<Int, Int>())


//data class TSWorld()
val receivedPacketRegistry = mapOf(
        0x0601 to PlayerWalkPacket::class,
        0x0C to PlayerAppearPacket::class,
        0x1408 to ActionOverPacket::class,
        0x1704 to ItemsInMapPacket::class,
        0x04 to PlayerShortInfoPacket::class,
        0x03 to PlayerOnlinePacket::class,
        0x1808 to PlayerUpdatePacket::class,
        0x0B0A to BattleStartedPacket::class,
        0x0B00 to BattleStopPacket::class,
        0x1407 to WarpSuccessPacket::class,
        0x1401 to NpcDialogPacket::class,
        0x1604 to NpcInMapPacket::class,
        0x1706 to ItemReceivedPacket::class,
        0x0504 to MapDisplayedOverPacket::class
)

val sentPacketRegistry = mapOf(
        0x00 to InitPacket::class,
        0x0108 to LoginPacket::class,
        0x4203 to OpenShop::class,
        0x0601 to WalkPacket::class,
        0x1408 to WarpPacket::class,
        0x1401 to ClickNPCPacket::class,
        0x1409 to ChooseMenuPacket::class,
        0x1406 to SendEndPacket::class,
        0x3201 to SendAttackPacket::class,
        0x0F05 to UnHorsePacket::class,
        0x1702 to PickItemPacket::class,
        0x0C01 to WarpFinishedAckPacket::class
)

interface TSFunction {
    val tsChar: TSChar
    val packetsPublisher: Observable<Packet>
    val gameEventPublisher: Observable<GameEvent>
    fun send(packet: SendablePacket, delay: Long = 0)
    fun handleProxyConnection(serverConn: Connection<ByteBuf, ByteBuf>, clientConnReq: ConnectionRequest<ByteBuf, ByteBuf>): Observable<Void>
    fun warpTo(mapId: Int)
    fun stopWarping()
    fun sendConfirmation() {
        send(SendEndPacket(), 500)
    }

    fun walkTo(x: Int?, y: Int?)
    fun chooseOption(choiceId: Int) {
        send(ChooseMenuPacket(choiceId))
    }

    fun talkTo(npcId: Int) {
        send(ClickNPCPacket(npcId))
    }

    fun warpVia(warpId: Int) {
        send(WarpPacket(warpId))
    }

    fun pickItemWithId(itemId: Int)
}

data class PacketWithDelay(val packet: SendablePacket, val delay: Long = 0)

class TSConnectionHandler(val appController: AppController) : TSFunction {
    private val packetsLogPublisher = PublishSubject.create<Packet>()
    private var userCancelAction: Boolean = false
    private val charStatusPublisher = PublishSubject.create<CharacterStatus>()
    private val gameEvents = PublishSubject.create<GameEvent>()
    private val manualSendPacketPublisher = PublishSubject.create<PacketWithDelay>()
    private val manualInjectToAlogin = PublishSubject.create<Packet>()
    private val isWarping
        get() = walkDirectionsQueue.isNotEmpty()
    private var warpState: Int = 0

    private val walkDirectionsQueue = ArrayDeque<MapDirection>()
    private var targetWarpMapId: Int? = null
    private var isDialogShowing: Boolean = false
    private var targetItemPicked: Int? = null
    private var itemsInMap = mutableListOf<ItemInMap>()

    override val packetsPublisher: Observable<Packet> = packetsLogPublisher.publish().refCount()
    override val gameEventPublisher: Observable<GameEvent> = gameEvents.publish().refCount()
    override val tsChar = TSChar()

    init {
    }

    override fun handleProxyConnection(serverConn: Connection<ByteBuf, ByteBuf>, clientConnReq: ConnectionRequest<ByteBuf, ByteBuf>): Observable<Void> {
        //Write back to Alogin by using client's input.
        return serverConn.writeAndFlushOnEach(clientConnReq
                .flatMap { clientConn ->
                    //Send to server by using proxy connection's input merge with manual send publisher
                    clientConn.writeAndFlushOnEach(
                            serverConn.input
                                    .observeOn(JavaFxScheduler.getInstance())
                                    .filter { packet ->
                                        onPacketSent(packet)
                                    }
                                    .observeOn(Schedulers.computation())
                                    .map { buffer ->
                                        for (i in 0..(buffer.readableBytes() - 1)) {
                                            buffer.setByte(buffer.readerIndex() + i, buffer.getByte(buffer.readerIndex() + i).toInt().xor(0xAD))
                                        }
                                        buffer
                                    }
                                    .doOnError {
                                        it.printStackTrace()
                                    }
                                    .mergeWith(manualSendPacketPublisher
                                            .onBackpressureBuffer(1000)
                                            .concatMap {
                                                Observable.just(it.packet).delay(it.delay, TimeUnit.MILLISECONDS)
                                            }
                                            .map { it ->
                                                handlePacketSent(it)
                                                it.toBytePacket()
                                            }
                                    ))
                            .cast(ByteBuf::class.java)
                            .mergeWith(clientConn.input)
                }
                .mergeWith(manualInjectToAlogin.map {
                    it.toBytePacket()
                })
                .observeOn(JavaFxScheduler.getInstance())
                .filter { packet ->
                    onPacketReceived(packet)
                }
                .observeOn(Schedulers.computation())
                .map { buffer ->
                    for (i in 0..(buffer.readableBytes() - 1)) {
                        buffer.setByte(buffer.readerIndex() + i, buffer.getByte(buffer.readerIndex() + i).toInt().xor(0xAD))
                    }
                    buffer
                }.doOnError {
            it.printStackTrace()
        }
        )
    }

    override fun send(packet: SendablePacket, delay: Long) {
        manualSendPacketPublisher.onNext(PacketWithDelay(packet, delay))
    }

    private fun onPacketSent(bytePacket: ByteBuf?): Boolean? {
        val packet = bytePacket?.splitPacket(sentPacketRegistry, true)
        return handlePacketSent(packet as SendablePacket)
    }

    private fun handlePacketSent(packet: SendablePacket): Boolean {
        packetsLogPublisher.onNext(packet)
        when (packet) {
            is LoginPacket -> {
                resetData(packet)
            }
            is SendAttackPacket -> {
                //TODO (D.Vu): player manually run so stop warping, check if attack is sent from main char/pet
                if (packet.skillID == 18001
                        && (packet.sourcePos == BattlePos(3, 2) || packet.sourcePos == BattlePos(2, 2))) {
//                    walkStepsLog.add("User chooses to stop cancel action")
                    userCancelAction = true
                }
            }
            is WarpFinishedAckPacket -> {
                if (warpState == 1) {
                    send(SendEndPacket())
                }
            }
            is WalkPacket -> {
                tsChar.x.set(packet.x)
                tsChar.y.set(packet.y)
                gameEvents.onNext(WalkFinished(tsChar.x.get(), tsChar.y.get()))
            }
        }
        return true
    }

    private fun onPacketReceived(bytePacket: ByteBuf?): Boolean {
        val packet = bytePacket?.splitPacket(receivedPacketRegistry)
        if (packet != null) {
            packetsLogPublisher.onNext(packet)
        }
        when (packet) {
            is PlayerOnlinePacket -> {
                if (packet.playerId == tsChar.id.get()) {
                    tsChar.mapId.set(packet.getRawShortLE(7))
                    tsChar.x.set(packet.getRawShortLE(9))
                    tsChar.y.set(packet.getRawShortLE(11))
                }
            }
            is BattleStartedPacket -> {
                isDialogShowing = false
                userCancelAction = false
                charStatusPublisher.onNext(CharacterStatus.BATTLING)
                gameEvents.onNext(BattleStarted())
            }
            is BattleStopPacket -> {
                if (tsChar.id.get() == packet.battleUid) {
                    if (isWarping) {
                        send(SendEndPacket())
                        if (userCancelAction) {
                            stopWarping()
                        }
                    }
                    charStatusPublisher.onNext(CharacterStatus.IDLE)
                    gameEvents.onNext(BattleEnded())
                }
                userCancelAction = false
            }
            is NpcDialogPacket -> {
                isDialogShowing = true
                if (isWarping) {
                    send(SendEndPacket())
                }
                if (packet.type.toInt() == 6) {
                    gameEvents.onNext(MenuAppear())
                } else {
                    gameEvents.onNext(DialogAppear(packet.dialogId, packet.type))
                }
            }
            is ActionOverPacket -> {
                if (isWarping) {
                    return false
                }
                if (isDialogShowing) {
                    gameEvents.onNext(TalkFinished())
                    isDialogShowing = false
                }
            }
            is PlayerAppearPacket -> {
                when {
                    (packet.playerId == tsChar.id.get()) -> {
                        tsChar.mapId.set(packet.mapId)
                        tsChar.x.set(packet.x)
                        tsChar.y.set(packet.y)
                        return !handleWarping()

                    }
                }
            }
            is PlayerWalkPacket -> {
                if (packet.partyId == tsChar.id.get()) {
                    tsChar.x.set(packet.x)
                    tsChar.y.set(packet.y)
                }
            }
            is NpcInMapPacket -> {
                gameEvents.onNext(NpcAppeared())
            }
            is ItemsInMapPacket -> {
                itemsInMap.clear()
                itemsInMap.addAll(packet.items)
                targetItemPicked?.let {
                    pickItemWithId(it)
                }
                gameEvents.onNext(ItemAppeared(packet.items))
            }
            is ItemReceivedPacket -> {
                gameEvents.onNext(ItemReceived(packet.itemId))
            }
            is WarpSuccessPacket -> {
                if (isWarping) {
                    return false
                }
            }
            is MapDisplayedOverPacket -> {
                if (warpState == 1) {
                    warpState = 0
                    gameEvents.onNext(WarpingEnded())
                }
            }
        }
        return true
    }

    private fun resumeWarping() {
        val warpStep = walkDirectionsQueue.peek()
        if (warpStep != null) {
            gameEvents.onNext(warpStep)
            send(WarpPacket(warpStep.warpId))
        }
    }

    private fun handleWarping(): Boolean {
        if (isWarping) {
            val currentWarpStep = walkDirectionsQueue.poll()
            if (currentWarpStep.targetId == tsChar.mapId.get()) {
                val warpStep = walkDirectionsQueue.peek()
                if (warpStep != null) {
                    sendFinishedWarpPacket()
                    send(WarpPacket(warpStep.warpId))
                    gameEvents.onNext(warpStep)
                    return true
                } else {
                    //Warp finish?
                    stopWarping()
//                    sendFinishedWarpPacket()
//                    send(SendEndPacket())
                    warpState = 1
                }
            } else {
                targetWarpMapId?.let {
                    sendFinishedWarpPacket()
                    warpTo(it)
                    return true
                }
            }
        } else {
            targetWarpMapId = null
        }
        return false
    }

    private fun sendFinishedWarpPacket() {
        send(RawSendablePacket(byteArrayOf(0x41, 0x01, 0x3C, 0x3C, 0x3C, 0x3C, 0x0, 0x0, 0x0, 0x0)))
        send(RawSendablePacket(byteArrayOf(0x17, 0x30)))
        send(RawSendablePacket(byteArrayOf(0x0C, 0x01)))
        send(SendEndPacket())
    }

    private fun resetData(packet: LoginPacket) {
        tsChar.id.set(packet.id)
        tsChar.mapId.set(0)
        tsChar.x.set(0)
        tsChar.y.set(0)
        targetWarpMapId = null
        targetItemPicked = null
        itemsInMap.clear()
        charStatusPublisher.onNext(CharacterStatus.IDLE)
        walkDirectionsQueue.clear()
    }

    override fun warpTo(mapId: Int) {
        stopWarping()
        targetWarpMapId = mapId
        val shortestPathAlgo = DijkstraShortestPath<Int, DefaultWeightedEdge>(appController.mapGraph)
        val currentMapId = tsChar.mapId.get()
        if (currentMapId != mapId) {
            val path = shortestPathAlgo.getPath(currentMapId, mapId)
            if (path != null && path.length > 0) {
                gameEvents.onNext(PathCalculated(path))
                val graph = path.graph
                path.edgeList.forEach {
                    val edgeWeight = graph.getEdgeWeight(it)
                    walkDirectionsQueue.add(MapDirection(graph.getEdgeSource(it), graph.getEdgeTarget(it), edgeWeight.toInt()))
                }
                //TODO (D.Vu): Fix it to wait for char to be idle before warping
                resumeWarping()
            } else {
                gameEvents.onNext(PathCalculated(null))
            }
        } else {
            stopWarping()
            gameEvents.onNext(WarpingEnded())
        }
    }

    val MAX_DISTANCE = 400
    val WALK_DELAY = 300
    override fun walkTo(x: Int?, y: Int?) {
        if (x != null && y != null) {
            var currentX = tsChar.x.get()
            var currentY = tsChar.y.get()
            var count = 1L
            while (currentX != x) {
                if (currentX <= x) {
                    currentX += Math.min(MAX_DISTANCE, x - currentX)
                    send(WalkPacket(WalkDirection.RIGHT, currentX, currentY), WALK_DELAY * count)
                } else {
                    currentX -= Math.min(MAX_DISTANCE, currentX - x)
                    send(WalkPacket(WalkDirection.LEFT, currentX, currentY), WALK_DELAY* count)
                }
                count++
            }
            while (currentY != y) {
                if (currentY <= y) {
                    currentY += Math.min(MAX_DISTANCE, y - currentY)
                    send(WalkPacket(WalkDirection.UP, currentX, currentY), WALK_DELAY * count)
                } else {
                    currentY -= Math.min(MAX_DISTANCE, currentY - y)
                    send(WalkPacket(WalkDirection.DOWN, currentX, currentY), WALK_DELAY * count)
                }
                count++
            }
        } else {
            gameEvents.onNext(WalkFinished(tsChar.x.get(), tsChar.y.get()))
        }
    }

    override fun pickItemWithId(itemId: Int) {
        targetItemPicked = itemId
        for (item in itemsInMap) {
            if (item.itemId == itemId) {
                targetItemPicked = null
                send(PickItemPacket(item.indexInMap))
                break
            }
        }
    }

    override fun stopWarping() {
        walkDirectionsQueue.clear()
    }
}

val defaultServerIP = "121.201.42.128"
val defaultPort = 6414

class AppController(val targetServerIP: String = defaultServerIP, val targetPort: Int = defaultPort) : Controller() {
    val proxyServerIP = "127.0.0.1"
    val proxyPort = 6414
    private lateinit var server: TcpServer<ByteBuf, ByteBuf>
    //    private val connectionMap = mutableMapOf<>()
    val mapList = FXCollections.observableMap<Int, MapData>(TreeMap())
    val mapGraph = DefaultDirectedWeightedGraph<Int, DefaultWeightedEdge>(DefaultWeightedEdge::class.java)
    val mapConnectionHandler = FXCollections.observableHashMap<String, TSFunction>()

    fun onStart() {
        ResourceLeakDetector.setLevel(ResourceLeakDetector.Level.ADVANCED)
        server = TcpServer.newServer(proxyPort)
//                .enableWireLogging("ABC", LogLevel.INFO)
                .addChannelHandlerLast<ByteBuf, ByteBuf>("Send packet decoder", { TSPacketDecoder() })
                .start { serverConn ->
                    println("Got new connection $serverConn ${Thread.currentThread()}")
                    val connectionId = serverConn.hashCode().toString()
                    serverConn.addChannelHandlerLast<ByteBuf, ByteBuf>("", TSConnectionManager(connectionId, mapConnectionHandler))
                    val client = TcpClient.newClient(targetServerIP, targetPort)
//                            .enableWireLogging("Send", LogLevel.INFO)
                            .addChannelHandlerLast<ByteBuf, ByteBuf>("Receive packet decoder", { TSPacketDecoder() })
                    val clientConnReq = client.createConnectionRequest()
                    val tsConnectionHandler = TSConnectionHandler(this)
//                    packetHandler.value = tsConnectionHandler
                    mapConnectionHandler[connectionId] = tsConnectionHandler
                    tsConnectionHandler.handleProxyConnection(serverConn, clientConnReq)
                }
    }

    fun cleanUp() {
        server.shutdown()
    }

    fun loadStaticData() {
        val warpFile = File("WarpID.ini")
        println("Read warp info from ${warpFile.absolutePath}")
        val warpDirectionRegExp = Regex("1\t([0-9]+)\t(\\d+)\t([0-9]+)")
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

}

class TSConnectionManager(val connectionId: String, val connectionMap: ObservableMap<String, TSFunction>) : ChannelInboundHandlerAdapter() {
    override fun channelActive(ctx: ChannelHandlerContext?) {
        super.channelActive(ctx)
        println("Channel active $connectionId")
    }

    override fun channelInactive(ctx: ChannelHandlerContext?) {
        super.channelInactive(ctx)
        println("Channel inactive $connectionId")
        connectionMap.remove(connectionId)
    }
}

class TSPacketDecoder : ByteToMessageDecoder() {
    override fun channelActive(ctx: ChannelHandlerContext?) {
        super.channelActive(ctx)
    }

    override fun channelInactive(ctx: ChannelHandlerContext?) {
        super.channelInactive(ctx)
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext?, cause: Throwable?) {
        super.exceptionCaught(ctx, cause)
        cause?.printStackTrace()
    }

    override fun decode(ctx: ChannelHandlerContext?, buffer: ByteBuf?, out: MutableList<Any>?) {
        decodeBuffer(buffer, { byteBuf ->
            out?.add(byteBuf)
        })
    }

    private fun decodeBuffer(buffer: ByteBuf?, packetProcessor: (ByteBuf) -> Unit) {
        buffer?.let {
            try {
                if (buffer.getByte(0).toInt() == 0x43) {
                    buffer.skipBytes(33)
                }
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
                            decodedBuffer.readerIndex(decodedBuffer.readerIndex() - 4)
                            val transformedPacket = decodedBuffer.readBytes(packetSize + 4)
                            packetProcessor(transformedPacket)
                            buffer.readerIndex(buffer.readerIndex() + packetSize + 4)
                        } else {
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
}

fun <T : Packet> ByteBuf.splitPacket(registry: Map<Int, KClass<out T>>, isDecodingSendPacket: Boolean = false): Packet {
    if (readableBytes() > 0) {
        val packetSize = readableBytes() - 4
        val startReaderIndex = 4
        readerIndex(startReaderIndex)
        val actionPrefix = readUnsignedByte().toInt()
        val actionSuffix = if (readableBytes() > 0) readUnsignedByte().toInt() else null
        val action = actionSuffix?.or(actionPrefix.shl(8)) ?: actionPrefix.shl(8)
        val tsPacket = when {
            registry.containsKey(action) -> {
                val byteArray = ByteArray(maxOf(packetSize - 2, 0))
                if (packetSize > 2) {
                    getBytes(startReaderIndex + 2, byteArray)
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
                getBytes(startReaderIndex + 1, byteArray)
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
                getBytes(startReaderIndex, byteArray)
                if (isDecodingSendPacket) {
                    RawSendablePacket(byteArray)
                } else {
                    RawPacket(byteArray)
                }
            }
        }
        resetReaderIndex()
        return tsPacket
    } else {
        println("Packet has no data")
        throw error("Packet has no data")
    }
}

private fun <K, V> Map<K, V>.toMutableMap(init: () -> Array<Pair<K, V>>): MutableMap<K, V> {
    val newMap = toMutableMap()
    newMap.putAll(init())
    return newMap
}


