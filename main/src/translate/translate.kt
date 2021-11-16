package translate

import PetriGame
import Arc
import CUSPT
import Place
import Switch
import Transition
import kotlinx.serialization.*
import kotlinx.serialization.json.Json
import org.redundent.kotlin.xml.*
import java.nio.file.Path

// Constants used for consistent naming
const val nfaPrefix = "NFA"
const val topologyPrefix = "TOPOLOGY"
const val updatePrefix = "UPDATE"
const val switchPrefix = "SWITCH"

data class PetriGameQueryPath(val petriGame: PetriGame, val queryPath: Path, val updateSwitchCount: Int)

private data class Edge(val from: Switch, val to: Switch) {
    override fun toString(): String {
        return "$from --> $to"
    }
}

private infix fun Switch.edge(other: Switch) = Edge(this, other)

fun generatePetriGameFromCUSPT(cuspt: CUSPT): PetriGameQueryPath {
    // Sets so duplicates cannot occur
    val places: MutableSet<Place> = mutableSetOf()
    val transitions: MutableSet<Transition> = mutableSetOf()
    val arcs: MutableSet<Arc> = mutableSetOf()

    val switchToTopologyPlaceMap: MutableMap<Switch, Place> = mutableMapOf()
    val switchToTopologyUnvisitedPlaceMap: MutableMap<Switch, Place> = mutableMapOf()
    val edgeToTopologyTransitionMap: MutableMap<Edge, Transition> = mutableMapOf()
    val switchComponentsFinalPlaces: MutableSet<Place> = mutableSetOf()

    // Network Topology Components
    // Create places. Everyone has a placekeeper place and a yet-to-be-visited place,
    for (s: Int in cuspt.allSwitches) {
        val p = Place(0, "${topologyPrefix}_P_$s")
        val pUnvisited = Place(2, "${topologyPrefix}_UV_$s")

        switchToTopologyPlaceMap[s] = p
        switchToTopologyUnvisitedPlaceMap[s] = pUnvisited

        places.add(p)
        places.add(pUnvisited)
    }

    // Create shared transitions
    for ((source, nextHops) in (cuspt.initialRouting.entries + cuspt.finalRouting.entries)) {
        for (nextHop in nextHops) {
            val t = Transition(true, "${topologyPrefix}_T_" + source + "_" + nextHop)
            transitions.add(t)
            edgeToTopologyTransitionMap[source edge nextHop] = t

            // Add arc from place to transition
            val place: Place = switchToTopologyPlaceMap[source]!!
            arcs.add(Arc(place, t, 1))

            // Add arc from unvisitedplace to transition
            val uPlace: Place? = switchToTopologyUnvisitedPlaceMap[nextHop]
            assert(uPlace != null)
            arcs.add(Arc(uPlace!!, t, 1))

            // Add arc from transition to nextHop node
            val tPlace: Place? = switchToTopologyPlaceMap[nextHop]
            assert(tPlace != null)
            arcs.add(Arc(t, tPlace!!, 1))
        }
    }

    // Initial and final equivalence classes
    val onlyInFinal = cuspt.initialRouting.filter { it.value.isEmpty() }.map { it.key }.toSet()
    val onlyInInitial = cuspt.finalRouting.filter { it.value.isEmpty() }.map { it.key }.toSet()

    // Switch Components
    // Find u \in V where R^i(u) != R^f(u)

    // Only switches that are different in final and initial routing
    val updatableSwitches = (cuspt.initialRouting.entries.toList() + cuspt.finalRouting.entries.toList())
        .groupBy { it.key }.filter { it.value.size != 2 || it.value[0] != it.value[1] }.map { it.key }

    val updatableNonTrivialSwitches: Set<Int> =
        updatableSwitches.filter { it !in onlyInFinal && it !in onlyInInitial }.toSet()
    var numSwitchComponents: Int
    // Update State Component
    val maxInBatch =
        if (Options.maxSwicthesInBatch != 0) { //Only add if option is set
            numSwitchComponents = Options.maxSwicthesInBatch
            Options.maxSwicthesInBatch
        } else
            updatableNonTrivialSwitches.size + if (onlyInInitial.isEmpty()) 0 else 1 + if (onlyInFinal.isEmpty()) 1 else 0
    numSwitchComponents = updatableNonTrivialSwitches.size + if (onlyInInitial.isEmpty()) 0 else 1 + if (onlyInFinal.isEmpty()) 0 else 1


    val pQueueing = Place(1, "${updatePrefix}_P_QUEUEING").apply { places.add(this) }
    val pUpdating = Place(0, "${updatePrefix}_P_UPDATING").apply { places.add(this) }
    val pBatches = Place(0, "${updatePrefix}_P_BATCHES").apply { places.add(this) }
    val pInvCount = Place(maxInBatch, "${updatePrefix}_P_INVCOUNT").apply { places.add(this) }
    val pCount = Place(0 + if (onlyInFinal.isEmpty()) 0 else 1, "${updatePrefix}_P_COUNT").apply { places.add(this) }
    val tConup = Transition(true, "${updatePrefix}_T_CONUP").apply { transitions.add(this) }
    val tReady = Transition(true, "${updatePrefix}_T_READY").apply { transitions.add(this) }

    arcs.add(Arc(pCount, tConup, 1))
    arcs.add(Arc(tConup, pCount, 1))
    arcs.add(Arc(pQueueing, tConup, 1))
    arcs.add(Arc(tConup, pUpdating, 1))
    arcs.add(Arc(tConup, pBatches, 1))
    arcs.add(Arc(pUpdating, tReady, 1))
    arcs.add(Arc(tReady, pQueueing, 1))
    arcs.add(Arc(pInvCount, tReady, numSwitchComponents))
    arcs.add(Arc(tReady, pInvCount, numSwitchComponents))

    val nontrivialSwitchComponentPG = mutableSetOf<PetriGame>()
    for (s: Set<Switch> in updatableNonTrivialSwitches.map { setOf(it) }) {
        nontrivialSwitchComponentPG += createSwitchComponent(s, false, cuspt, edgeToTopologyTransitionMap, switchComponentsFinalPlaces, pCount, pInvCount, pQueueing, pUpdating)
    }

    val initialEqClassSwitchComponentPG = createSwitchComponent(onlyInInitial, true, cuspt, edgeToTopologyTransitionMap, switchComponentsFinalPlaces, pCount, pInvCount, pQueueing, pUpdating)
    val finalEqClassSwitchComponentPG = createSwitchComponent(onlyInFinal, false, cuspt, edgeToTopologyTransitionMap, switchComponentsFinalPlaces, pCount, pInvCount, pQueueing, pUpdating)

    for (pg in nontrivialSwitchComponentPG + initialEqClassSwitchComponentPG + finalEqClassSwitchComponentPG) {
        places.addAll(pg.places)
        transitions.addAll(pg.transitions)
        arcs.addAll(pg.arcs)
    }

    // Visited Places are already handled previously

    // Packet Injection Component
    val tInject = Transition(false, "PACKET_INJECT_T")
    transitions.add(tInject)
    arcs.add(Arc(pUpdating, tInject, 1))
    arcs.add(Arc(tInject, switchToTopologyPlaceMap[cuspt.ingressSwitch]!!, 1))
    arcs.add(Arc(switchToTopologyUnvisitedPlaceMap[cuspt.ingressSwitch]!!, tInject, 1))

    // NFA
    // First we translate the NFA into a Petri Game
    val (nfaPetriGame, nfaStateToPlaceMap, nfaActionToTransitionMap) = cuspt.policy.toPetriGame()

    // Add all information from nfa petri to this full petri
    places.addAll(nfaPetriGame.places)
    transitions.addAll(nfaPetriGame.transitions)
    arcs.addAll(nfaPetriGame.arcs)

    // NFA tracking component
    val turnSwitch = Place(0, "${nfaPrefix}_TURN").apply { places.add(this) }
    val switchToTrackPlace = mutableMapOf<Int, Place>()

    for (s in cuspt.allSwitches) {
        val trackPlace = Place(0, "${nfaPrefix}_TRACK_Pswitch${s}")
        switchToTrackPlace[s] = trackPlace
    }
    places.addAll(switchToTrackPlace.values)

    // Create nfa actions transitions
    for (a in cuspt.policy.actions) {
        val nfaSwitchTransition = nfaActionToTransitionMap[a]!!
        arcs.add(Arc(switchToTrackPlace[a.label]!!, nfaSwitchTransition))
    }

    for ((e, t) in edgeToTopologyTransitionMap) {
        arcs.add(Arc(t, switchToTrackPlace[e.to]!!))
    }

    arcs.add(Arc(tInject, turnSwitch))

    // Arcs from turn place to topology transitions
    for (t in edgeToTopologyTransitionMap.values)
        arcs.add(Arc(turnSwitch, t))

    // Arcs from NFA transitions to turn place
    for (t in nfaPetriGame.transitions) {
        arcs.add(Arc(t, turnSwitch))
    }

    // Generate the query
    val queryPath = kotlin.io.path.createTempFile("query")
    val NFAFinalStatePlace = nfaStateToPlaceMap[cuspt.policy.finalStates.first()]!!

    queryPath.toFile().writeText(generateQuery(switchComponentsFinalPlaces, NFAFinalStatePlace))

    return PetriGameQueryPath(PetriGame(places, transitions, arcs), queryPath, numSwitchComponents)
}

private fun createSwitchComponent(s: Set<Switch>, initial: Boolean, cuspt: CUSPT, edgeToTopologyTransitionMap: Map<Edge, Transition>, switchComponentsFinalPlaces: MutableSet<Place>, pCount: Place, pInvCount: Place, pQueueing: Place, pUpdating: Place): PetriGame {
    val eqClass = s.size > 1
    val name = s.joinToString(separator = "s") { it.toString() }

    val places = mutableSetOf<Place>()
    val transitions = mutableSetOf<Transition>()
    val arcs = mutableSetOf<Arc>()

    // Make sure the initial edge is different to its final correspondent
    val pInit = Place(1, "${switchPrefix}_P_${name}_INIT").apply { places.add(this) }
    val pQueue = Place(if (eqClass && !initial) 1 else 0, "${switchPrefix}_P_${name}_QUEUE").apply { places.add(this) }
    val pFinal = Place(0, "${switchPrefix}_P_${name}_FINAL").apply { places.add(this); switchComponentsFinalPlaces.add(this) }
    val pLimiter = Place(if (eqClass && !initial) 0 else 1, "${switchPrefix}_P_${name}_LIMITER").apply { places.add(this) }
    val tQueue = Transition(true, "${switchPrefix}_T_${name}_QUEUE").apply { transitions.add(this) }
    val tUpdate = Transition(false, "${switchPrefix}_T_${name}_UPDATE").apply { transitions.add(this) }

    arcs.add(Arc(tQueue, pCount, 1))
    arcs.add(Arc(pCount, tUpdate, 1))
    arcs.add(Arc(pInit, tQueue, 1))
    arcs.add(Arc(tQueue, pInit, 1))
    arcs.add(Arc(pLimiter, tQueue, 1))
    arcs.add(Arc(tQueue, pQueue, 1))
    arcs.add(Arc(pInit, tUpdate, 1))
    arcs.add(Arc(pQueue, tUpdate, 1))
    arcs.add(Arc(tUpdate, pFinal, 1))
    arcs.add(Arc(pInvCount, tQueue, 1))
    arcs.add(Arc(tQueue, pQueueing, 1))
    arcs.add(Arc(pQueueing, tQueue, 1))
    arcs.add(Arc(pUpdating, tUpdate, 1))
    arcs.add(Arc(tUpdate, pUpdating, 1))
    arcs.add(Arc(tUpdate, pInvCount, 1))

    for (s_i in s) {
        val initialNextHops = cuspt.initialRouting[s_i] ?: setOf()
        val finalNextHops = cuspt.finalRouting[s_i] ?: setOf()

        for (nextHop in initialNextHops) {
            arcs.add(Arc(pInit, edgeToTopologyTransitionMap[s_i edge nextHop]!!))
            arcs.add(Arc(edgeToTopologyTransitionMap[s_i edge nextHop]!!, pInit))
        }

        for (nextHop in finalNextHops) {
            arcs.add(Arc(pFinal, edgeToTopologyTransitionMap[s_i edge nextHop]!!))
            arcs.add(Arc(edgeToTopologyTransitionMap[s_i edge nextHop]!!, pFinal))
        }
    }
    return PetriGame(places, transitions, arcs)
}

data class NFAToPetriGame(
    val petriGame: PetriGame,
    val stateToPlaceMap: Map<NFA.State, Place>,
    val actionToTransitionMap: Map<NFA.Action, Transition>
)

fun NFA.toPetriGame(): NFAToPetriGame {
    val arcs: MutableSet<Arc> = mutableSetOf()
    val stateToPlaceMap = states.associateWith {
        if (it.type == NFA.StateType.INITIAL) Place(1, "${nfaPrefix}_Pstate_${it.name}")
        else Place(0, "${nfaPrefix}_Pstate_${it.name}")
    }
    var i = 0
    val actionToTransitionMap = actions.associateWith {
        Transition(true, "${nfaPrefix}_T${i++}_Switch${it.label}")
    }

    for (aToT in actionToTransitionMap) {
        arcs.add(Arc(stateToPlaceMap[aToT.key.from]!!, aToT.value))
        arcs.add(Arc(aToT.value, stateToPlaceMap[aToT.key.to]!!))
    }

    return NFAToPetriGame(
        PetriGame(stateToPlaceMap.values.toSet(), actionToTransitionMap.values.toSet(), arcs.toSet()),
        stateToPlaceMap,
        actionToTransitionMap
    )
}

fun updateSynthesisModelFromJsonText(jsonText: String): UpdateSynthesisModel {
    // HACK: Change waypoint with 1 element of type int to type list of ints
    val regex = """waypoint": (\d+)""".toRegex()
    val text = regex.replace(jsonText) { m ->
        "waypoint\": [" + m.groups[1]!!.value + "]"
    }

    // Update Synthesis Model loaded from json
    return Json.decodeFromString<UpdateSynthesisModel>(text)
}

fun generatePnmlFileFromPetriGame(petriGame: PetriGame, modelPath: Path): String {
    val pnml = xml("pnml") {
        xmlns = """http://www.pnml.org/version-2009/grammar/pnml"""
        "net" {
            attribute("id", """ComposedModel""")
            attribute("type", """http://www.pnml.org/version-2009/grammar/ptnet""")

            "page" {
                attribute("id", """page0""")
                for (p: Place in petriGame.places) {
                    "place" {
                        attribute("id", p.name)
                        "graphics" {
                            "position" {
                                attribute("x", p.pos.first)
                                attribute("y", p.pos.second)
                            }
                        }
                        "name" {
                            "graphics" {
                                "offset" {
                                    attribute("x", "0")
                                    attribute("y", "0")
                                }
                            }
                            "text" {
                                -p.name
                            }
                        }
                        "initialMarking" {
                            "text" {
                                attribute("removeWhitespace", "")
                                -p.initialTokens.toString()
                            }
                        }
                    }
                }
                for (t: Transition in petriGame.transitions) {
                    "transition" {
                        attribute("id", t.name)
                        "player" {
                            "value" {
                                -if (t.controllable) "0" else "1"
                            }
                        }

                        "name" {
                            "graphics" {
                                "offset" {
                                    attribute("x", "0")
                                    attribute("y", "0")
                                }
                            }
                            "text" {
                                -t.name
                            }
                        }
                        "graphics" {
                            "position" {
                                attribute("x", t.pos.first)
                                attribute("y", t.pos.second)
                            }
                        }
                    }
                }
                for (a: Arc in petriGame.arcs) {
                    "arc" {
                        attribute("id", a.name)
                        attribute("source", a.source.name)
                        attribute("target", a.target.name)
                        attribute("type", "normal")
                        if (a.weight > 1) {
                            "inscription" {
                                "text" {
                                    attribute("removeWhitespace", "")
                                    -a.weight.toString()
                                }
                            }
                        }
                    }
                }
            }

            "name" {
                "text" {
                    -"ComposedModel"
                }
            }
        }
    }

    // Apply some initial xml-format
    var res = """<?xml version="1.0" encoding="UTF-8" standalone="no"?>""" + "\n" + pnml.toString()

    // Tapaal does not know xml.. so we must remove some newlines before and after ints
    res = res.replace("""<([^\s]*) removeWhitespace="">\s*([^\s]+)\s*""".toRegex(), "<$1>$2")

    modelPath.toFile().writeText(res)

    return res
}

fun generateQuery(mustBeFinalSwitchComponents: Set<Place>, finalNFAState: Place): String {
    var query = "EF (UPDATE_P_BATCHES <= 0 and ((UPDATE_P_QUEUEING = 1"

    for (place in mustBeFinalSwitchComponents) {
        query += " and ${place.name} = 1"
    }

    query += ") or (${finalNFAState.name} = 1)))"
    return query
}

