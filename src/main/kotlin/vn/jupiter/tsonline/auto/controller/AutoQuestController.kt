package vn.jupiter.tsonline.auto.controller

import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleIntegerProperty
import javafx.beans.property.SimpleStringProperty
import javafx.collections.FXCollections
import rx.Subscription
import tornadofx.*
import vn.jupiter.tsonline.auto.app.ConnectionScope
import vn.jupiter.tsonline.auto.data.*
import vn.jupiter.tsonline.auto.utils.JavaFxScheduler
import java.io.File

/**
 * Created by jupiter on 7/2/17.
 */
class AutoQuestController : Controller() {
    val mainController by inject<MainController>()
    val tsFunction: TSFunction = (scope as ConnectionScope).tsFunction
    val listOfQuests = FXCollections.observableArrayList<Quest>()
    val currentRunningQuestSteps = FXCollections.observableArrayList<QuestStep>()
    val currentStep = SimpleIntegerProperty(-1)
    val isQuestRecording = SimpleBooleanProperty()
    val questNameProperty = SimpleStringProperty()
    var recordQuestSubscription: Subscription? = null
    private var isAllowToGoNextStep: Boolean = true

    init {
        currentStep.onChange { newValue ->
            if (newValue < currentRunningQuestSteps.size && newValue >= 0) {
                val questStep = currentRunningQuestSteps[currentStep.get()]
                tsFunction.warpTo(questStep.mapId)
            } else {
                currentStep.set(-1)
            }
        }
        isQuestRecording.onChange {
            if (it) {
                currentRunningQuestSteps.clear()
                currentStep.set(-1)
                recordQuestSubscription = tsFunction.gameEventPublisher
                        .observeOn(JavaFxScheduler.getInstance())
                        .subscribe { gameEvent ->
                            val currentMapId = tsFunction.tsChar.mapId.get()
                            val x = tsFunction.tsChar.x.get()
                            val y = tsFunction.tsChar.y.get()
                            println("Record quest ${gameEvent}")
                            when (gameEvent) {
                                is NpcClicked -> {
                                    currentRunningQuestSteps += TalkToNpc(currentMapId, x, y, gameEvent.npcId)
                                }
                                is MenuChosen -> {
                                    currentRunningQuestSteps.lastOrNull()?.let {
                                        if (it is TalkToNpc) {
                                            currentRunningQuestSteps[currentRunningQuestSteps.size - 1] = it.copy(choiceId = gameEvent.choiceId)
                                        } else if (it is WarpToId) {
                                            currentRunningQuestSteps[currentRunningQuestSteps.size - 1] = it.copy(choiceId = gameEvent.choiceId)
                                        }
                                    }
                                }
                                is MapDirection -> {
                                    currentRunningQuestSteps += WarpToId(currentMapId, x, y, gameEvent.warpId)
                                }
                                is ItemReceived -> {
                                    currentRunningQuestSteps += PickItemWithId(currentMapId, x, y, gameEvent.itemId)
                                }
                                is MapChanged -> {
                                    currentRunningQuestSteps.remove(currentRunningQuestSteps.lastOrNull {
                                        it is WarpToId && it.mapId == gameEvent.sourceMapId
                                    })
                                }
                            }
                        }
            } else {
                recordQuestSubscription?.unsubscribe()

            }
        }
        tsFunction.gameEventPublisher
                .observeOn(JavaFxScheduler.getInstance())
                .subscribe { gameEvent ->
                    if (currentStep.get() >= 0 && currentStep.get() < currentRunningQuestSteps.size) {
                        val questStep = currentRunningQuestSteps[currentStep.get()]
                        val tsChar = tsFunction.tsChar
                        println("${System.currentTimeMillis()} Game event $questStep $gameEvent ${tsChar.mapId.get()} ${tsChar.x.get()} ${tsChar.y.get()}")
                        when (gameEvent) {
                            is WarpingEnded -> {
                                if (tsChar.mapId.value == questStep.mapId) {
                                    tsFunction.walkTo(questStep.x, questStep.y)
                                }
                            }
                            is WalkFinished -> {
                                if (questStep.x == tsChar.x.get() && questStep.y == tsChar.y.get()) {
                                    when (questStep) {
                                        is WarpToId -> {
                                            tsFunction.warpVia(questStep.warpId)
                                        }
                                        is SpecialWarpToId -> {
                                            tsFunction.warpVia(questStep.warpId, true)
                                        }
                                        is PickItemWithId -> {
                                            val isAlreadyHasItem = tsFunction.tsChar.inventory.any { it.itemId == questStep.itemId }
                                            if (!isAlreadyHasItem) {
                                                println("Wait to pick item: ${questStep.itemId}")
                                                tsFunction.waitToPickItem(questStep.itemId)
                                            } else {
                                                println("Already has item ${questStep.itemId}")
                                                proceedToNextStep()
                                            }
                                        }
                                        is TalkToNpc -> if (tsChar.mapId.value == questStep.mapId) {
                                            tsFunction.talkTo(questStep.npcId)
                                        }
                                    }
                                }
                            }
                            is WarpSameMapFinished -> {
                                if (questStep is WarpToId || questStep is SpecialWarpToId) {
                                    proceedToNextStep()
                                }
                            }
                            is BattleEnded -> {
                                if (questStep !is PickItemWithId) {
                                    if (tsChar.mapId.value == questStep.mapId) {
                                        if (questStep.x == tsChar.x.get() && questStep.y == tsChar.y.get()) {
                                            tsFunction.sendConfirmation()
                                        }
                                    }
                                }
                            }
                            is ItemReceived -> {
                                if (questStep is PickItemWithId) {
                                    if (gameEvent.itemId == questStep.itemId) {
                                        proceedToNextStep()
                                    }
                                }
                            }
                            is MenuAppear -> {
                                if (questStep.choiceId != null) {
                                    tsFunction.chooseOption(questStep.choiceId!!)
                                } else {
                                    println("There is menu but no choice specified")
                                }
                            }
                            is DialogAppear -> {
                                if (questStep !is PickItemWithId) {
                                    val dialogType = gameEvent.dialogType.toInt()
                                    if (dialogType != 0x6 && dialogType != 0x0) {
                                        if (dialogType == 0x5) {//Animation will happens -> should wait
                                            tsFunction.sendConfirmation(20000)
                                        } else {
                                            tsFunction.sendConfirmation(400)
                                        }
                                    } else if (dialogType == 0x0) {
                                        tsFunction.enqueueSendEnd(4)
                                    }
                                }
                            }

                            is TalkFinished -> {
                                if (questStep !is PickItemWithId) {
                                    proceedToNextStep()
                                }
                            }
                        }
                    }
                }
    }

    private fun proceedToNextStep() {
        if (isAllowToGoNextStep) {
            println("Go to Next Step")
            currentStep.set(currentStep.get() + 1)
        } else {
            currentStep.set(-1)
        }
    }

    fun loadDodoQuest(questFolder: File) {
        val questInfoFile = File(questFolder, "NPCInfo.ini")
        val questOrderFile = File(questFolder, "NPCBatch.ini")
        println("Load quest from $questInfoFile")
        currentRunningQuestSteps.clear()
        val questInfoLine = questOrderFile.readLines()[0]
        val questInfos = questInfoLine.split("=")
        questNameProperty.set(questInfos[0])
        val questStepsOrder = questInfos[1].split(",")
        val mapQuestInfo = mutableMapOf<String, QuestStep>()
        questInfoFile.forEachLine { line ->
            val splitInfos = line.split("=")
            val stepName = splitInfos[0]
            val stepMapId = splitInfos[1].toInt()
            val npcId = splitInfos[2].toInt()
            val stepCoordinate = splitInfos[3]
            val warpType = splitInfos[4].toInt()
            val warpId = splitInfos[5].toInt()
            val choiceId = splitInfos[6].toIntOrNull()
            val itemId = splitInfos[7].trim().toInt()
            var x: Int? = null
            var y: Int? = null
            if (stepCoordinate.isNotEmpty()) {
                var coordinates = stepCoordinate.split(",")
                if (coordinates.isEmpty()) {
                    coordinates = stepCoordinate.split('.')
                }
                x = coordinates[0].toInt()
                y = coordinates[1].toInt()
            }
            val questStep = when {
                warpType == 1 -> WarpToId(stepMapId, x, y, warpId, choiceId, stepName)
                warpType == 4 -> SpecialWarpToId(stepMapId, x, y, warpId, choiceId, stepName)
                itemId != 0 -> PickItemWithId(stepMapId, x, y, itemId, stepName)
                npcId != 0 -> TalkToNpc(stepMapId, x, y, npcId, choiceId, stepName)
                else -> null
            }
            questStep?.let {
                mapQuestInfo[stepName] = questStep
            }
        }
        for (stepName in questStepsOrder) {
            mapQuestInfo[stepName]?.let {
                currentRunningQuestSteps += it
            }
        }

    }

    fun saveRecoredQuest(questDirectory: File?) {
        val npcInfoFile = File(questDirectory, "NPCInfo.ini")
        val npcBatchFile = File(questDirectory, "NPCBatch.ini")
        val npcBatchBuilder = StringBuilder()
        npcBatchBuilder.append("${questNameProperty.get()}=")
        for (questStep in currentRunningQuestSteps) {
            when (questStep) {
                is TalkToNpc -> {
                    npcInfoFile.writeText("${questStep.customName}=${questStep.mapId}=${questStep.npcId}=${questStep.x},${questStep.y}=0=0=${questStep.choiceId ?: 0}=0\n")
                }
                is WarpToId -> {
                    npcInfoFile.writeText("${questStep.customName}=${questStep.mapId}=0=${questStep.x},${questStep.y}=1=${questStep.warpId}=0=0\n")
                }
                is PickItemWithId -> {
                    npcInfoFile.writeText("${questStep.customName}=${questStep.mapId}=0=${questStep.x},${questStep.y}=0=0=0=${questStep.itemId}\n")
                }
            }
            npcBatchBuilder.append("${questStep.customName},")
        }
        npcBatchFile.writeText(npcBatchBuilder.toString())
    }

    fun startDoAutoQuest(index: Int = 0) {
        isAllowToGoNextStep = true
        currentStep.set(-1)
        currentStep.set(index)
    }

    fun reRunStep(index: Int) {
        isAllowToGoNextStep = false
        currentStep.set(-1)
        currentStep.set(index)
    }


    fun stopAutoQuest() {
        println("Stop quest")
        currentStep.set(-1)
    }

    fun deleteQuestAtIndex(selectedIndex: Int) {
        if (selectedIndex >= 0 && selectedIndex < currentRunningQuestSteps.size) {
            currentRunningQuestSteps.removeAt(selectedIndex)
        }
    }
}