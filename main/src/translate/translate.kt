package translate

import PetriGame
import Arc
import Place
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

fun generatePetriGameModelFromUpdateSynthesisNetwork(usm: UpdateSynthesisModel, policyNFA: NFA): PetriGameQueryPath {
    // Sets so duplicates cannot occur
    val places: MutableSet<Place> = mutableSetOf()
    val transitions: MutableSet<Transition> = mutableSetOf()
    val arcs: MutableSet<Arc> = mutableSetOf()

    val switchToTopologyPlaceMap: MutableMap<Int, Place> = mutableMapOf()
    val switchToUnvisitedPlaceMap: MutableMap<Int, Place> = mutableMapOf()
    val edgeToTopologyTransitionMap: MutableMap<Edge, Transition> = mutableMapOf()

    val initialNode = usm.reachability.initialNode
    val finalNode = usm.reachability.finalNode

    // TODO: We do not consider undefinity at all, and that is probably fine

    // Network Topology Components
    // Create places. Everyone has a placekeeper place and a yet-to-be-visited place,
    val allSwitches = (usm.initialRouting + usm.finalRouting).let {
            allEdges -> allEdges.map { it.source } + allEdges.map { it.target }
        }.distinct()
    for (s: Int in allSwitches) {
        val p = Place(0, "${topologyPrefix}_P_$s")
        val pUnvisited = Place(2, "${topologyPrefix}_UV_$s")

        switchToTopologyPlaceMap[s] = p
        switchToUnvisitedPlaceMap[s] = pUnvisited

        places.add(p)
        places.add(pUnvisited)
    }

    // Create shared transitions
    for (edge: Edge in (usm.initialRouting + usm.finalRouting).distinct()) {
        val t = Transition(true, "${topologyPrefix}_T_" + edge.source + "_" + edge.target)
        transitions.add(t)
        edgeToTopologyTransitionMap[edge] = t

        // Add arc from place to transition
        val place: Place? = switchToTopologyPlaceMap[edge.source]
        assert(place != null)
        arcs.add(Arc(place!!, t, 1))

        // Add arc from unvisitedplace to transition
        val uPlace: Place? = switchToUnvisitedPlaceMap[edge.target]
        assert(uPlace != null)
        arcs.add(Arc(uPlace!!, t, 1))

        // Add arc from transition to target node
        val tPlace: Place? = switchToTopologyPlaceMap[edge.target]
        assert(tPlace != null)
        arcs.add(Arc(t, tPlace!!, 1))
    }

    // Switch Components
    // Find u \in V where R^i(u) != R^f(u) // TODO: This can be optimized by finding iEdge and fEdge simultaneously
    val updatableSwitches: Set<Int> = (
            ((usm.initialRouting union usm.finalRouting) subtract (usm.initialRouting intersect usm.finalRouting))
            .map { it.source }).toSet()

    // Update State Component
    val pQueueing = Place(1, "${updatePrefix}_P_QUEUEING").apply { places.add(this) }
    val pUpdating = Place(0, "${updatePrefix}_P_UPDATING").apply { places.add(this) }
    val pBatches = Place(0, "${updatePrefix}_P_BATCHES").apply { places.add(this) }
    val pInvCount = Place(updatableSwitches.count(), "${updatePrefix}_P_INVCOUNT").apply { places.add(this) }
    val pCount = Place(0, "${updatePrefix}_P_COUNT").apply { places.add(this) }
    val tConup = Transition(true, "${updatePrefix}_T_CONUP").apply { transitions.add(this) }
    val tReady = Transition(true, "${updatePrefix}_T_READY").apply { transitions.add(this) }

    arcs.add(Arc(pCount, tConup, 1))
    arcs.add(Arc(tConup, pCount, 1))
    arcs.add(Arc(pQueueing, tConup, 1))
    arcs.add(Arc(tConup, pUpdating, 1))
    arcs.add(Arc(tConup, pBatches, 1))
    arcs.add(Arc(pUpdating, tReady, 1))
    arcs.add(Arc(tReady, pQueueing, 1))
    arcs.add(Arc(pInvCount, tReady, updatableSwitches.count()))
    arcs.add(Arc(tReady, pInvCount, updatableSwitches.count()))

    for (u: Int in updatableSwitches) {
        val iEdge = usm.initialRouting.find { it.source == u }
        val fEdge = usm.finalRouting.find { it.source == u }

        // Make sure the initial edge is different to its final correspondent
        val pInit = Place(1, "${switchPrefix}_P_${u}_INIT").apply { places.add(this) }
        val pQueue = Place(0, "${switchPrefix}_P_${u}_QUEUE").apply { places.add(this) }
        val pFinal = Place(0, "${switchPrefix}_P_${u}_FINAL").apply { places.add(this) }
        val pLimiter = Place(1, "${switchPrefix}_P_${u}_LIMITER").apply { places.add(this) }
        val tQueue = Transition(true, "${switchPrefix}_T_${u}_QUEUE").apply { transitions.add(this) }
        val tUpdate = Transition(false, "${switchPrefix}_T_${u}_UPDATE").apply { transitions.add(this) }

        arcs.add(Arc(tQueue, pCount, 1))
        arcs.add(Arc(pCount, tUpdate, 1))
        arcs.add(Arc(pInit, tQueue, 1))
        arcs.add(Arc(tQueue, pInit, 1))
        arcs.add(Arc(pLimiter, tQueue, 1))
        arcs.add(Arc(tQueue, pQueue, 1))
        arcs.add(Arc(pInit, tUpdate, 1))
        arcs.add(Arc(pQueue, tUpdate, 1))
        arcs.add(Arc(tUpdate, pFinal, 1))
        arcs.add(Arc(pInvCount, tQueue, 1,))
        arcs.add(Arc(tQueue, pQueueing, 1))
        arcs.add(Arc(pQueueing, tQueue, 1))
        arcs.add(Arc(pUpdating, tUpdate, 1))
        arcs.add(Arc(tUpdate, pUpdating, 1))
        arcs.add(Arc(tUpdate, pInvCount, 1))

        if (iEdge != null) {
            arcs.add(Arc(pInit, edgeToTopologyTransitionMap[iEdge]!!, 1))
            arcs.add(Arc(edgeToTopologyTransitionMap[iEdge]!!, pInit, 1))
        }
        if (fEdge != null) {
            arcs.add(Arc(pFinal, edgeToTopologyTransitionMap[fEdge]!!, 1))
            arcs.add(Arc(edgeToTopologyTransitionMap[fEdge]!!, pFinal, 1))
        }
    }


    // Visited Places are already handled previously

    // Packet Injection Component
    val tInject = Transition(false, "PACKET_INJECT_T")
    transitions.add(tInject)
    arcs.add(Arc(pUpdating, tInject, 1))
    arcs.add(Arc(tInject, switchToTopologyPlaceMap[initialNode]!!, 1))
    arcs.add(Arc(switchToUnvisitedPlaceMap[initialNode]!!, tInject, 1))

    // NFA
    // First we translate the NFA into a Petri Game
    val (nfaPetriGame, nfaStateToPlaceMap, nfaActionToTransitionMap) = policyNFA.toPetriGame()

    // Add all information from nfa petri to this full petri
    places.addAll(nfaPetriGame.places)
    transitions.addAll(nfaPetriGame.transitions)
    arcs.addAll(nfaPetriGame.arcs)

    // NFA tracking component
    val turnSwitch = Place(1, "${nfaPrefix}_TURN").apply { places.add(this) }
    val switchToTrackPlace = mutableMapOf<Int, Place>()

    for (s in allSwitches) {
        val trackPlace = Place(0, "${nfaPrefix}_TRACK_Pswitch${s}")
        switchToTrackPlace[s] = trackPlace
    }
    places.addAll(switchToTrackPlace.values)

    // Create nfa actions transitions
    for (a in policyNFA.actions) {
        val nfaSwitchTransition = nfaActionToTransitionMap[a]!!
        arcs.add(Arc(switchToTrackPlace[a.label]!!, nfaSwitchTransition))
    }

    for ((e, t) in edgeToTopologyTransitionMap) {
        arcs.add(Arc(t, switchToTrackPlace[e.target]!!))
    }

    arcs.add(Arc(turnSwitch, tInject))
    // Handle initial state in NFA
    // Add arc from tInject to initial place from NFA
    arcs.add(Arc(tInject, nfaStateToPlaceMap[policyNFA.initialState]!!))
    arcs.add(Arc(tInject, switchToTrackPlace[usm.reachability.initialNode]!!))

    val initialSwitchTransitions = policyNFA.outgoing(policyNFA.initialState!!)
        .filter { it.label == usm.reachability.initialNode }.map { nfaActionToTransitionMap[it]!! }

    for (t in initialSwitchTransitions) {
        arcs.add(Arc(switchToTrackPlace[usm.reachability.initialNode]!!, t))
    }

    // Arcs from turn place to topology transitions
    for (t in edgeToTopologyTransitionMap.values)
        arcs.add(Arc(turnSwitch, t))

    // Arcs from NFA transitions to turn place
    for (t in nfaPetriGame.transitions) {
        arcs.add(Arc(t, turnSwitch))
    }

    // Generate the query
    val queryPath = kotlin.io.path.createTempFile("query")
    val switchNames = updatableSwitches.map { "${switchPrefix}_P_${it}_FINAL" }

    val acceptingPlaces = nfaStateToPlaceMap.filterKeys { it.type == NFA.StateType.FINAL }.values.map { it.name }
    queryPath.toFile().writeText(generateQuery(switchNames, acceptingPlaces))

    return PetriGameQueryPath(PetriGame(places, transitions, arcs), queryPath, updatableSwitches.count())
}

data class NFAToPetriGame(val petriGame: PetriGame,
                          val stateToPlaceMap: Map<NFA.State, Place>,
                          val actionToTransitionMap: Map<NFA.Action, Transition>)

fun NFA.toPetriGame(): NFAToPetriGame {
    val arcs: MutableSet<Arc> = mutableSetOf()
    val stateToPlaceMap = states.associateWith { Place(0, "${nfaPrefix}_Pstate_${it.name}") }
    var i = 0
    val actionToTransitionMap = actions.associateWith {
        Transition(true, "${nfaPrefix}_T${i++}_Switch${it.label}")
    }

    for (aToT in actionToTransitionMap) {
        arcs.add(Arc(stateToPlaceMap[aToT.key.from]!!, aToT.value))
        arcs.add(Arc(aToT.value, stateToPlaceMap[aToT.key.to]!!))
    }



    return NFAToPetriGame(PetriGame(stateToPlaceMap.values.toSet(), actionToTransitionMap.values.toSet(), arcs.toSet()), stateToPlaceMap, actionToTransitionMap)
}

fun updateSynthesisModelFromJsonText(jsonText: String): UpdateSynthesisModel {
    // HACK: Change waypoint with 1 element of type int to type list of ints
    val regex = """waypoint": (\d+)""".toRegex()
    val text = regex.replace(jsonText) {
            m -> "waypoint\": [" + m.groups[1]!!.value + "]"
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
                                -p.initialMarkings.toString()
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

fun generateQuery(switches: List<String>, acceptingStates: List<String>):  String{
    var query = "EF (UPDATE_P_BATCHES <= 0 and ("
    query += "(UPDATE_P_QUEUEING = 1"
    for (switch in switches){
        query += " and $switch = 1"
    }
    query += ") or ("
    query += "${acceptingStates[0]} = 1"
    for (state in acceptingStates.drop(1)){
        query += " or $state = 1"
    }
    query += ")))"
    return query
}

