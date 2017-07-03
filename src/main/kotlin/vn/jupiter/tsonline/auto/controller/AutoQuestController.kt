package vn.jupiter.tsonline.auto.controller

import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleIntegerProperty
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
    val questSteps = FXCollections.observableArrayList<QuestStep>()
    val currentStep = SimpleIntegerProperty(-1)
    val isQuestRecording = SimpleBooleanProperty()
    var recordQuestSubscription: Subscription? = null

    init {
        currentStep.onChange { newValue ->
            if (newValue < questSteps.size && newValue >= 0) {
                val questStep = questSteps[currentStep.get()]
                tsFunction.warpTo(questStep.mapId)
            } else {
                currentStep.set(-1)
            }
        }
        isQuestRecording.onChange {
            if (it) {
                questSteps.clear()
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
                                    //TODO (D.Vu): Handle choice?
                                    questSteps += TalkToNpc(currentMapId, x, y, gameEvent.npcId)
                                }
                                is MenuChosen -> {

                                }
                            //TODO (D.Vu): Warp via something?
                            }
                        }
            } else {
                recordQuestSubscription?.unsubscribe()
                //TODO (D.Vu): Save?
            }
        }
        tsFunction.gameEventPublisher
                .observeOn(JavaFxScheduler.getInstance())
                .subscribe { gameEvent ->
                    if (currentStep.get() >= 0 && currentStep.get() < questSteps.size) {
                        val questStep = questSteps[currentStep.get()]
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
                                        is WarpToId -> tsFunction.warpVia(questStep.warpId)
                                        is PickItemWithId -> tsFunction.pickItemWithId(questStep.itemId)
                                        is TalkToNpc -> if (tsChar.mapId.value == questStep.mapId) {
                                            tsFunction.talkTo(questStep.npcId)
                                        }
                                    }
                                }
                            }
                            is BattleEnded -> {
                                if (questStep is TalkToNpc || questStep is WarpToId) {
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
                                if (questStep is TalkToNpc) {
                                    if (questStep.choiceId != null) {
                                        tsFunction.chooseOption(questStep.choiceId)
                                    } else {
                                        println("There is menu but no choice specified")
                                    }
                                }
                            }
                            is DialogAppear -> {
                                if (questStep is TalkToNpc) {
                                    if (gameEvent.dialogType.toInt() == 0x1) {
                                        tsFunction.sendConfirmation()
                                    }
                                }
                            }

                            is TalkFinished -> {
                                if (questStep is TalkToNpc) {
                                    proceedToNextStep()
                                }
                            }

                        }
                    }
                }
    }

    private fun proceedToNextStep() {
        currentStep.set(currentStep.get() + 1)
    }

    fun loadDodoQuest(questFolder: File) {
        val questInfoFile = File(questFolder, "NPCInfo.ini")
        val questOrderFile = File(questFolder, "NPCBatch.ini")
        println("Load quest from $questInfoFile")
        questSteps.clear()
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
                    coordinates = stepCoordinate.split(".")
                }
                x = coordinates[0].toInt()
                y = coordinates[1].toInt()
            }
            val questStep = when {
                warpType == 1 -> WarpToId(stepMapId, x, y, warpId)
                itemId != 0 -> PickItemWithId(stepMapId, x, y, itemId)
                npcId != 0 -> TalkToNpc(stepMapId, x, y, npcId, choiceId)
                else -> null
            }
            questStep?.let {
                questSteps += it
            }
        }
    }

    fun loadQuest() {

    }

    fun saveRecoredQuest() {

    }

    fun startDoAutoQuest(index: Int = 0) {
        currentStep.set(-1)
        currentStep.set(index)
    }

    fun pauseAutoQuest() {

    }

    fun stopAutoQuest() {
        println("Stop quest")
        currentStep.set(-1)
    }
}