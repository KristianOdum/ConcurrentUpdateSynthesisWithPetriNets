package translate

import guru.nidi.graphviz.attribute.Attributes
import guru.nidi.graphviz.engine.Format
import guru.nidi.graphviz.graph
import guru.nidi.graphviz.model.MutableGraph
import guru.nidi.graphviz.toGraphviz
import java.io.File

class NFA(val states: MutableSet<State> = mutableSetOf(), val actions: MutableSet<Action> = mutableSetOf()) {
    val initialState: State?
        get() = states.find { it.type == StateType.INITIAL }

    val finalStates: Set<State>
        get() = states.filter { it.type == StateType.FINAL }.toSet()

    fun addAction(a: Action): NFA {
        actions += a

        if (!states.contains(a.from))
            addState(a.from)
        if (!states.contains(a.to))
            addState(a.to)

        return this
    }

    fun setStatesByActions() {
        states.clear()
        states.addAll(actions.fold(mutableSetOf()) { acc, action -> acc.apply { add(action.from); add(action.to) } })
    }

    fun addState(s: State): NFA {
        states += s
        return this
    }

    fun copy(): NFA {
        return NFA(states.toMutableSet(), actions.toMutableSet())
    }

    fun outgoing(s: State) = actions.filter { it.from == s }

    fun ingoing(s: State) = actions.filter { it.to == s }
}


open class Action(val from: State, val label: String, val to: State) {
    init {
        assert(label != "eps" || (label == "eps" && this is EpsilonAction))
    }

    override fun equals(other: Any?): Boolean =
        other is Action
                && other.from == this.from
                && other.to == other.to
                && other.label == this.label

    override fun hashCode(): Int {
        var result = from.hashCode()
        result = 31 * result + label.hashCode()
        result = 31 * result + to.hashCode()
        return result
    }

    override fun toString(): String = from.name + "  -${label}->  " + to.name
}

class EpsilonAction(from: State, to: State) : Action(from, "eps", to) {
    override fun equals(other: Any?): Boolean =
        other is Action
                && other.from == this.from
                && other.to == other.to

    override fun hashCode(): Int {
        var result = from.hashCode()
        result = 31 * result + to.hashCode()
        return result
    }

    override fun toString(): String {
        return super.toString() + "  EPSILON"
    }
}

class State(val name: String, val type: StateType = StateType.NORMAL) {
    override fun equals(other: Any?): Boolean =
        other is State
                && other.name == this.name
                && other.type == this.type

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + type.hashCode()
        return result
    }

    override fun toString(): String = "${name},   " + type.name
}

enum class StateType {
    NORMAL, INITIAL, FINAL
}

fun generateNFAFromUSMProperties(usm: UpdateSynthesisModel): NFA {
    // NFA for reachability
    val reachabilityNFA = genReachabilityNFA(usm)

    // NFA for waypoint
    val combinedWaypointNFA = genCombinedWaypointNFA(usm)

    // Intersect the reachability NFA with the waypoints
    return combinedWaypointNFA intersect reachabilityNFA
}

fun genCombinedWaypointNFA(usm: UpdateSynthesisModel): NFA{
    val waypoints = waypointNFAs(usm)
    return waypoints.reduce { acc:NFA, it: NFA -> acc intersect it }
}

fun genReachabilityNFA(usm: UpdateSynthesisModel): NFA {
    val sI = State("1", StateType.INITIAL)
    val sJ = State("2")
    val sF = State("3", StateType.FINAL)
    val nfa = NFA().addAction(Action(sI, usm.reachability.initialNode.toString(), sJ))
        .apply {
            for (s in (usm.switches subtract setOf(usm.reachability.finalNode)))
                addAction(Action(sJ, s.toString(), sJ))
        }
        .addAction(Action(sJ, usm.reachability.finalNode.toString(), sF))

    return nfa
}

fun waypointNFAs(usm: UpdateSynthesisModel): Set<NFA> {
    val sI = State("1", StateType.INITIAL)
    val sJ = State("2", StateType.FINAL)
    val res = mutableSetOf<NFA>()

    for (w in usm.waypoint.waypoints) {
        val nfa = NFA()

        for (s in (usm.switches subtract setOf(w))) {
            nfa.addAction(Action(sI, s.toString(), sI))
            nfa.addAction(Action(sJ, s.toString(), sJ))
        }

        nfa.addAction(Action(sI, w.toString(), sJ))
        nfa.addAction(Action(sJ, w.toString(), sJ))

        res.add(nfa)
    }

    return res
}

infix fun NFA.intersect(other: NFA): NFA {
    val labels = (actions union other.actions).map { it.label }.toSet()
    val newStatesByPair = mutableSetOf<Pair<State, State>>()
    val newStateToState = mutableMapOf<Pair<State, State>, State>()
    val newActions = mutableSetOf<Action>()

    // generate states of the intersection NFA
    var nextId = 0
    for (s1 in states) {
        for (s2 in other.states) {
            newStatesByPair += Pair(s1, s2)

            val type = if (s1.type != s2.type)
                StateType.NORMAL
            else if (s1.type == StateType.FINAL && s2.type == StateType.FINAL)
                StateType.FINAL
            else if (s1.type == StateType.INITIAL && s2.type == StateType.INITIAL)
                StateType.INITIAL
            else
                StateType.NORMAL

            newStateToState[Pair(s1, s2)] = State((++nextId).toString(), type)
        }
    }

    // create arcs between new states
    for (s1 in states) {
        for (s2 in other.states) {
            for (l in labels) {
                // there can be multiple actions with the same label in NFA
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

    val res = NFA()
    res.actions.addAll(newActions)
    res.setStatesByActions()
    return res
}

fun NFA.prune() {
    pruneByDirection(forward = true)
    pruneByDirection(forward = false)
}

fun NFA.pruneByDirection(forward: Boolean) {
    val nextActions: MutableSet<Action> =
        if (forward) {
            val sI = initialState
            if (sI != null)
                mutableSetOf(actions.find { it.from == initialState }!!)
            else
                throw Exception("Tried to do forward pruning of NFA, but no initial state is present in NFA.")
        } else {
            if (finalStates.isNotEmpty())
                actions.filter { finalStates.contains(it.to) }.toMutableSet()
            else
                return
        }
    val actionsNotReached = (actions subtract nextActions.toMutableSet()).toMutableSet()

    while (nextActions.isNotEmpty()) {
        val action = nextActions.first()
        actionsNotReached.remove(action)

        nextActions += actionsNotReached.filter { if (forward) it.from == action.to else it.to == action.from }

        nextActions.remove(action)
    }

    actions.removeAll(actionsNotReached)
    this.setStatesByActions()
}

fun NFA.toGraphviz(path: String) {
    val graph: MutableGraph = graph(directed = true) {
        for (a in actions) {
            for (s in listOf(a.from, a.to)) {
                if (s.type == StateType.FINAL)
                    s.name[Attributes.attrs(listOf(Attributes.attr("shape", "doublecircle")))]
                else if (s.type == StateType.INITIAL)
                    s.name[Attributes.attrs(
                        listOf(
                            Attributes.attr("fillcolor", "gray"),
                            Attributes.attr("style", "filled")
                        )
                    )]
            }

            if (a is EpsilonAction)
                (a.from.name - a.to.name).add(Attributes.attr("label", "eps"))
            else
                (a.from.name - a.to.name).add(Attributes.attr("label", a.label))
        }
    }
    if (Options.drawGraphs)
        graph.toGraphviz().render(Format.PNG)
            .toFile(File("graphics_out/" + if (path.endsWith(".png")) path else "$path.png"))
}

fun NFA.export(path: String){
    var output = "States:"
    output += states.joinToString(",") { it.name }
    output += "\nInitial state:${initialState!!.name}"
    output += "\nFinal states:"
    output += finalStates.joinToString(",") { it.name }
    output += "\nActions:"
    output += actions.joinToString(separator = ";") { "${it.from.name},${it.to.name},${it.label}" }

    File("${path}.txt").writeText(output)
}