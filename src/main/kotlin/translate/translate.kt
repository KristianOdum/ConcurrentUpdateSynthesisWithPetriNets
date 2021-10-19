package translate

import PetriGame
import Arc
import Place
import Transition
import kotlinx.serialization.*
import kotlinx.serialization.json.Json
import org.redundent.kotlin.xml.*
import java.io.File

fun generatePetriGameModelFromUpdateNetworkJson(jsonText: String): PetriGame {
    // HACK: Change waypoint with 1 element of type int to type list of ints
    val regex = """waypoint": (\d+)""".toRegex()
    val text = regex.replace(jsonText) {
            m -> "waypoint\": [" + m.groups[1]!!.value + "]"
    }

    // Update Synthesis Model loaded from json
    val usm = Json.decodeFromString<UpdateSynthesisModel>(text)

    // Sets so duplicates cannot occur
    val places: MutableSet<Place> = mutableSetOf()
    val transitions: MutableSet<Transition> = mutableSetOf()
    val arcs: MutableSet<Arc> = mutableSetOf()

    val switchToPlaceMap: MutableMap<Int, Place> = mutableMapOf()
    val switchToUnvisitedPlaceMap: MutableMap<Int, Place> = mutableMapOf()
    val edgeToTransitionMap: MutableMap<Edge, Transition> = mutableMapOf()

    val initialNode = usm.reachability.initialNode
    val finalNode = usm.reachability.finalNode
//    transitions.add(Transition(false, "INJECT"))

    // Constants used for consistent naming
    val topologyPrefix = "TOPOLOGY"
    val updatePrefix = "UPDATE"
    val switchPrefix = "SWITCH"

    // TODO: We do not consider undefinity at all, and that is probably fine

    // Network Topology Components
    // Create places. Everyone has a placekeeper place, an yet-to-be-visited place,
    val allSwitches = (usm.initialRouting + usm.finalRouting).let {
            allEdges -> allEdges.map { it.source } + allEdges.map { it.target }
        }.distinct()
    for (s: Int in allSwitches) {
        val p = Place(0, "${topologyPrefix}_P_$s")
        val pUnvisited = Place(2, "${topologyPrefix}_UV_$s")

        switchToPlaceMap[s] = p
        switchToUnvisitedPlaceMap[s] = pUnvisited

        places.add(p)
        places.add(pUnvisited)
    }

    // Create shared transitions
    for (edge: Edge in (usm.initialRouting + usm.finalRouting).distinct()) {
        val t = Transition(true, "${topologyPrefix}_T_" + edge.source + "_" + edge.target)
        transitions.add(t)
        edgeToTransitionMap[edge] = t

        // Add arc from place to transition
        val place: Place? = switchToPlaceMap[edge.source]
        assert(place != null)
        arcs.add(Arc(place!!, t, 1))

        // Add arc from unvisitedplace to transition
        val uPlace: Place? = switchToUnvisitedPlaceMap[edge.target]
        assert(uPlace != null)
        arcs.add(Arc(uPlace!!, t, 1))

        // Add arc from transition to target node
        val tPlace: Place? = switchToPlaceMap[edge.target]
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
    val pCount = Place(updatableSwitches.count(), "${updatePrefix}_P_COUNT").apply { places.add(this) }
    val tConup = Transition(true, "${updatePrefix}_T_CONUP").apply { transitions.add(this) }
    val tReady = Transition(true, "${updatePrefix}_T_READY").apply { transitions.add(this) }

    arcs.add(Arc(pQueueing, tConup, 1))
    arcs.add(Arc(tConup, pUpdating, 1))
    arcs.add(Arc(tConup, pBatches, 1))
    arcs.add(Arc(pUpdating, tReady, 1))
    arcs.add(Arc(tReady, pQueueing, 1))
    arcs.add(Arc(pCount, tReady, updatableSwitches.count()))
    arcs.add(Arc(tReady, pCount, updatableSwitches.count()))

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

        arcs.add(Arc(pInit, tQueue, 1))
        arcs.add(Arc(tQueue, pInit, 1))
        arcs.add(Arc(pLimiter, tQueue, 1))
        arcs.add(Arc(tQueue, pQueue, 1))
        arcs.add(Arc(pInit, tUpdate, 1))
        arcs.add(Arc(pQueue, tUpdate, 1))
        arcs.add(Arc(tUpdate, pFinal, 1))
        arcs.add(Arc(pCount, tQueue, 1,))
        arcs.add(Arc(tQueue, pQueueing, 1))
        arcs.add(Arc(pQueueing, tQueue, 1))
        arcs.add(Arc(pUpdating, tUpdate, 1))
        arcs.add(Arc(tUpdate, pUpdating, 1))
        arcs.add(Arc(tUpdate, pCount, 1))

        if (iEdge != null) {
            arcs.add(Arc(pInit, edgeToTransitionMap[iEdge]!!, 1))
            arcs.add(Arc(edgeToTransitionMap[iEdge]!!, pInit, 1))
        }
        if (fEdge != null) {
            arcs.add(Arc(pFinal, edgeToTransitionMap[fEdge]!!, 1))
            arcs.add(Arc(edgeToTransitionMap[fEdge]!!, pFinal, 1))
        }
    }


    // Visited Places are already handled previously

    // Packet Injection Component
    val tInject = Transition(true, "PACKET_INJECT_T")
    transitions.add(tInject)
    arcs.add(Arc(pUpdating, tInject, 1))
    arcs.add(Arc(tInject, switchToPlaceMap[initialNode]!!, 1))
    arcs.add(Arc(switchToUnvisitedPlaceMap[initialNode]!!, tInject, 1))

    return PetriGame(places, transitions, arcs)
}

fun generatePnmlFileFromPetriGame(petriGame: PetriGame, outputPath: String): String {
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
                                -if (t.controllable) "1" else "0"
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

    File(outputPath).writeText(res)

    return res
}

