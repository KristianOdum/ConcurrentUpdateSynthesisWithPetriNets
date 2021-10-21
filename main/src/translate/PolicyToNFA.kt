package translate

import guru.nidi.graphviz.attribute.Attributes
import guru.nidi.graphviz.attribute.Color
import guru.nidi.graphviz.attribute.Label
import guru.nidi.graphviz.engine.Format
import guru.nidi.graphviz.graph
import guru.nidi.graphviz.model.Link
import guru.nidi.graphviz.model.MutableGraph
import guru.nidi.graphviz.toGraphviz
import java.io.File

class NFA {
    val states: MutableSet<Int> = mutableSetOf()
    val actions: MutableSet<Action> = mutableSetOf()

    fun addAction(a: Action): NFA {
        actions += a

        if (!states.contains(a.from))
            states += a.from
        if (!states.contains(a.to))
            states += a.to

        return this
    }

    fun addAction(from: Int, label: Int, to: Int, epsilon: Boolean = false): NFA {
        addAction(Action(from, label, to, epsilon))
        return this
    }

    fun addState(a: Int): NFA {
        states += a
        return this
    }
}


open class Action(val from: Int, val label: Int, val to: Int, val epsilon: Boolean = false) {}

//class EpsilonAction(val from: Int, val to: Int) : Action(from, to) { }


fun generateNFAFromUSM(usm: UpdateSynthesisModel): NFA {
    val nfa = NFA()

    val initialSwitch = usm.reachability.initialNode
    val finalSwitch = usm.reachability.finalNode
    var currentId = 0
    val initialState = currentId++

    // NFA for reachability
    val reach = reachabilityNFA(currentId, usm)
    val reachNFA = reach.first
    currentId = reach.second
    graphOfNFA(reachNFA)

    // NFA for waypoint


    return nfa
}

fun reachabilityNFA(_id: Int, usm: UpdateSynthesisModel): Pair<NFA, Int> {
    var id = _id
    val sI = id++
    val sJ = id++
    val sF = id++
    val nfa = NFA().addAction(sI, usm.reachability.initialNode, sJ)
        .apply {
            for (s in (usm.switches subtract setOf(usm.reachability.finalNode)))
                addAction(sJ, s, sJ)
        }
        .addAction(sJ, usm.reachability.finalNode, sF)

    return Pair(nfa, id)
}

fun graphOfNFA(nfa: NFA) {
    val graph: MutableGraph = graph(directed = true) {
        for (a in nfa.actions) {
            if (a.epsilon)
                (a.from.toString() - a.to.toString()).add(Attributes.attr("label", "eps"))
            else
                (a.from.toString() - a.to.toString()).add(Attributes.attr("label", a.label.toString()))
        }
    }

    graph.toGraphviz().render(Format.PNG).toFile(File("nfa.png"))
}