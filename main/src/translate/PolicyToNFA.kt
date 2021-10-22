package translate

import guru.nidi.graphviz.attribute.Attributes
import guru.nidi.graphviz.engine.Format
import guru.nidi.graphviz.graph
import guru.nidi.graphviz.model.MutableGraph
import guru.nidi.graphviz.toGraphviz
import java.io.File

class NFA {
    val states: MutableSet<State> = mutableSetOf()
    var initialState: State = State(-1)
    val actions: MutableSet<Action> = mutableSetOf()

    fun addAction(a: Action): NFA {
        actions += a

        if (!states.contains(a.from))
            addState(a.from)
        if (!states.contains(a.to))
            addState(a.to)

        return this
    }

    fun addAction(from: State, label: Int, to: State, epsilon: Boolean = false): NFA {
        addAction(Action(from, label, to, epsilon))
        return this
    }

    fun addActions(_actions: List<Action>): NFA {
        actions += _actions
        return this
    }

    fun addState(s: State): NFA {
        states += s
        if (s.type == StateType.INITIAL)
            initialState = s
        return this
    }

    fun addStates(_states: List<State>): NFA {
        for (s in _states) {
            addState(s)
        }
        return this
    }
}


open class Action(val from: State, val label: Int, val to: State, val epsilon: Boolean = false) {
    override fun equals(other: Any?): Boolean =
        other is Action
                && other.from == this.from
                && other.to == other.to
                && other.label == this.label
                && other.epsilon == this.epsilon

    override fun hashCode(): Int {
        var result = from.id
        result = 31 * result + label
        result = 31 * result + to.id
        result = 31 * result + epsilon.hashCode()
        return result
    }
}

open class State(val id: Int, val type: StateType = StateType.NORMAL) {
    override fun hashCode(): Int {
        var result = id
        result = 31 * result + type.name.hashCode()
        return result
    }

    override fun equals(other: Any?): Boolean =
        other is State
                && other.id == this.id
                && other.type == this.type
}

enum class StateType {
    NORMAL, INITIAL, FINAL
}

fun generateNFAFromUSM(usm: UpdateSynthesisModel): NFA {
    // NFA for reachability
    val reachabilityNFA = reachabilityNFA(usm)

    // NFA for waypoint
    val waypoints = waypointNFAs(usm)

    val nfa = waypoints.first() intersect reachabilityNFA
    graphOfNFA(nfa, "pedro")

    return nfa
}

fun reachabilityNFA(usm: UpdateSynthesisModel): NFA {
    val sI = State(1, StateType.INITIAL)
    val sJ = State(2)
    val sF = State(3, StateType.FINAL)
    val nfa = NFA().addAction(Action(sI, usm.reachability.initialNode, sJ))
        .apply {
            for (s in (usm.switches subtract setOf(usm.reachability.finalNode)))
                addAction(Action(sJ, s, sJ))
        }
        .addAction(Action(sJ, usm.reachability.finalNode, sF))

    return nfa
}

fun waypointNFAs(usm: UpdateSynthesisModel): List<NFA> {
    val sI = State(1, StateType.INITIAL)
    val sJ = State(2)
    val sF = State(3, StateType.FINAL)
    val res = mutableListOf<NFA>()

    for (w in usm.waypoint.waypoints) {
        val nfa = NFA()

        for (s in (usm.switches subtract setOf(w))) {
            nfa.addAction(Action(sI, s, sI))
            nfa.addAction(Action(sJ, s, sJ))
        }

        nfa.addAction(Action(sI, w, sJ))
        nfa.addAction(Action(sJ, usm.reachability.finalNode, sF))

        res.add(nfa)
    }

    return res
}

infix fun NFA.intersect(other: NFA): NFA {
    val labels = (actions union other.actions).map { it.label }.toSet()
    val newStates = mutableSetOf<Pair<State, State>>()
    val newStateToState = mutableMapOf<Pair<State, State>, State>()
    val newActions = mutableSetOf<Action>()

    var nextId = 0
    for (s1 in states) {
        for (s2 in other.states) {
            newStates += Pair(s1, s2)

            val type = if (s1.type != s2.type)
                StateType.NORMAL
            else if (s1.type == StateType.FINAL && s2.type == StateType.FINAL)
                StateType.FINAL
            else if (s1.type == StateType.INITIAL && s2.type == StateType.INITIAL)
                StateType.INITIAL
            else
                StateType.NORMAL

            nextId = "${s1.id}${s2.id}".toInt()
            newStateToState[Pair(s1, s2)] = State(nextId, type)
        }
    }

    for (s1 in states) {
        for (s2 in other.states) {
            for (l in labels) {
                val a1s = actions.filter { it.from == s1 && it.label == l }
                val a2s = other.actions.filter { it.from == s2 && it.label == l }
                for (a1 in a1s) {
                    for (a2 in a2s) {
                        val from = newStateToState[Pair(s1, s2)]!!
                        val to = newStateToState[Pair(a1.to, a2.to)]!!

                        newActions += Action(from, l, to)
                    }
                }
            }
        }
    }

    return NFA().addActions(newActions.toList()).addStates(newStateToState.values.toList())
}

fun graphOfNFA(nfa: NFA, path: String) {
    val graph: MutableGraph = graph(directed = true) {
        for (a in nfa.actions) {
            if (a.from.type == StateType.FINAL)
                a.from.id.toString()[Attributes.attrs(listOf(Attributes.attr("shape", "doublecircle")))]
            else if (a.from.type == StateType.INITIAL)
                a.from.id.toString()[Attributes.attrs(
                    listOf(
                        Attributes.attr("fillcolor", "gray"),
                        Attributes.attr("style", "filled")
                    )
                )]

            if (a.to.type == StateType.FINAL)
                a.to.id.toString()[Attributes.attrs(listOf(Attributes.attr("shape", "doublecircle")))]
            else if (a.to.type == StateType.INITIAL)
                a.to.id.toString()[Attributes.attrs(
                    listOf(
                        Attributes.attr("fillcolor", "gray"),
                        Attributes.attr("style", "filled")
                    )
                )]

            if (a.epsilon)
                (a.from.id.toString() - a.to.id.toString()).add(Attributes.attr("label", "eps"))
            else
                (a.from.id.toString() - a.to.id.toString()).add(Attributes.attr("label", a.label.toString()))
        }
    }

    graph.toGraphviz().render(Format.PNG).toFile(File(if (path.endsWith(".png")) path else "$path.png"))
}

