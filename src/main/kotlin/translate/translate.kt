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
    // Update Synthesis Model loaded from json
    val usm = Json.decodeFromString<UpdateSynthesisModel>(jsonText)

    // Sets so duplicates cannot occur
    val places: MutableSet<Place> = mutableSetOf()
    val transitions: MutableSet<Transition> = mutableSetOf()
    val arcs: MutableSet<Arc> = mutableSetOf()

    val switchToPlaceMap: MutableMap<Int, Place> = mutableMapOf()
    val switchToUnvisitedPlaceMap: MutableMap<Int, Place> = mutableMapOf()
    val edgeToTransitionMap: MutableMap<Edge, Transition> = mutableMapOf()

    val initialNode = usm.reachability.startNode
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
        arcs.add(Arc(place!!.name, t.name, 1, false))

        // Add arc from unvisitedplace to transition
        val uPlace: Place? = switchToUnvisitedPlaceMap[edge.target]
        assert(uPlace != null)
        arcs.add(Arc(uPlace!!.name, t.name, 1, false))

        // Add arc from transition to target node
        val tPlace: Place? = switchToPlaceMap[edge.target]
        assert(tPlace != null)
        arcs.add(Arc(t.name, tPlace!!.name, 1, false))
    }

    // Update State Component
    val pQueueing = Place(0, "${updatePrefix}_P_QUEUEING").apply { places.add(this) }
    val pUpdating = Place(0, "${updatePrefix}_P_UPDATING").apply { places.add(this) }
    val pBatches = Place(0, "${updatePrefix}_P_BATCHES").apply { places.add(this) }
    val pCount = Place(0, "${updatePrefix}_P_COUNT").apply { places.add(this) }
    val tConup = Transition(true, "${updatePrefix}_T_CONUP").apply { transitions.add(this) }
    val tReady = Transition(true, "${updatePrefix}_T_READY").apply { transitions.add(this) }

    arcs.add(Arc(pQueueing.name, tConup.name, 1, false))
    arcs.add(Arc(tConup.name, pUpdating.name, 1, false))
    arcs.add(Arc(tConup.name, pBatches.name, 1, false))
    arcs.add(Arc(pCount.name, tConup.name, 1, false))
    arcs.add(Arc(tConup.name, pCount.name, 1, false))
    arcs.add(Arc(pUpdating.name, tReady.name, 1, false))
    arcs.add(Arc(tReady.name, pQueueing.name, 1, false))
    arcs.add(Arc(pCount.name, tReady.name, 1, true))

    // Switch Components
    for (edge: Edge in (usm.initialRouting + usm.finalRouting).toSet() subtract  (usm.initialRouting.toSet() intersect usm.finalRouting.toSet())) {
        val correspondent = usm.finalRouting.find { it.source == edge.source }
        // Make sure the initial edge is different to its final correspondent
        if (correspondent != null && edge.target != correspondent.target) {
            val pInit = Place(1, "${switchPrefix}_P_${edge.source}_INIT").apply { places.add(this) }
            val pQueue = Place(0, "${switchPrefix}_P_${edge.source}_QUEUE").apply { places.add(this) }
            val pFinal = Place(0, "${switchPrefix}_P_${edge.source}_FINAL").apply { places.add(this) }
            val pLimiter = Place(1, "${switchPrefix}_P_${edge.source}_LIMITER").apply { places.add(this) }
            val tQueue = Transition(true, "${switchPrefix}_T_${edge.source}_QUEUE").apply { transitions.add(this) }
            val tUpdate = Transition(false, "${switchPrefix}_T_${edge.source}_UPDATE").apply { transitions.add(this) }

            arcs.add(Arc(pInit.name, tQueue.name, 1, false))
            arcs.add(Arc(tQueue.name, pInit.name, 1, false))
            arcs.add(Arc(pLimiter.name, tQueue.name, 1, false))
            arcs.add(Arc(tQueue.name, pQueue.name, 1, false))
            arcs.add(Arc(pInit.name, tUpdate.name, 1, false))
            arcs.add(Arc(pQueue.name, tUpdate.name, 1, false))
            arcs.add(Arc(tUpdate.name, pFinal.name, 1, false))
            arcs.add(Arc(tQueue.name, pCount.name, 1, false))
            arcs.add(Arc(pUpdating.name, tUpdate.name, 1, false))
            arcs.add(Arc(tUpdate.name, pUpdating.name, 1, false))
            arcs.add(Arc(pQueue.name, pQueueing.name, 1, false))
            arcs.add(Arc(pQueueing.name, pQueue.name, 1, false))
            arcs.add(Arc(pCount.name, tUpdate.name, 1, false))

            arcs.add(Arc(pInit.name, edgeToTransitionMap[edge]!!.name, 1, false))
            arcs.add(Arc(edgeToTransitionMap[edge]!!.name, pInit.name, 1, false))
            arcs.add(Arc(pFinal.name, edgeToTransitionMap[correspondent]!!.name, 1, false))
            arcs.add(Arc(edgeToTransitionMap[correspondent]!!.name, pFinal.name, 1, false))
        }
    }

    // Visited Places are already handled previously

    // Packet Injection Component
    val tInject = Transition(true, "PACKET_INJECT_T")
    transitions.add(tInject)
    arcs.add(Arc(pUpdating.name, tInject.name, 1, false))
    arcs.add(Arc(tInject.name, switchToPlaceMap[initialNode]!!.name, 1, false))
    arcs.add(Arc(switchToUnvisitedPlaceMap[initialNode]!!.name, tInject.name, 1, false))

    return PetriGame(places.toList(), transitions.toList(), arcs.toList())
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
                                attribute("x", "0")
                                attribute("y", "0")
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
                                attribute("x", "0")
                                attribute("y", "0")
                            }
                        }
                    }
                }
                for (a: Arc in petriGame.arcs) {
                    "arc" {
                        attribute("id", a.name)
                        attribute("source", a.sourceName)
                        attribute("target", a.targetName)
                        attribute("type", if (a.inhibitor) "inhibitor" else "normal")
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

    val res = """<?xml version="1.0" encoding="UTF-8" standalone="no"?>\n""" + pnml.toString()
//    println(res)

    File(outputPath).writeText(res)

    return res
}

