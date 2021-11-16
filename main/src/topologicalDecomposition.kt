import translate.*
import translate.Edge
import java.util.*

typealias SCC = List<Int>
typealias SCCId = Int

fun topologicalDecomposition(cusp: CUSPT): List<CUSPT> {
    val pto = partialTopologicalOrder(cusp)
    val posDFAState = switchPossibleNFAStates(cusp, pto)
    val ptoi: Iterable<IndexedValue<SCC>> = pto.withIndex()
    val switchToSCCId: Map<Switch, SCCId> = ptoi.flatMap { (sccid, scc) -> scc.map { Pair(it, sccid) } }.toMap()

    val sccNarrowness = ptoi.associate { Pair(it.index, 0.0) }.toMutableMap() // SCC to Narrowness

    val sccId = ptoi.first { cusp.initialSwitch in it.value }.index
    sccNarrowness[sccId] = 1.0

    data class Subproblem(val switches: MutableSet<Switch>, val initSwitch: Switch, var finalSwitch: Switch)
    var currentSubproblem = Subproblem(mutableSetOf(), cusp.initialSwitch, -1)
    val subproblems = mutableListOf<Subproblem>()

    for ((i, scc) in ptoi) {
        if (sccNarrowness[i]!! == 1.0 && scc.size == 1 && posDFAState[scc.first()]!!.size == 1 && currentSubproblem.switches.isNotEmpty()) {
            currentSubproblem.switches.addAll(scc)
            currentSubproblem.finalSwitch = scc.first()
            subproblems.add(currentSubproblem)
            currentSubproblem = Subproblem(mutableSetOf(), scc.first(), -1)
        }

        for (s in scc) {
            val nexts = (cusp.initialRouting[s] ?: setOf()) union (cusp.finalRouting[s] ?: setOf())
            val outdegree = nexts.size

            nexts.filter { it !in scc }.map { switchToSCCId[it]!! }.forEach {
                sccNarrowness[it] = sccNarrowness[it]!! + (sccNarrowness[i]!! / outdegree)
            }
        }
        currentSubproblem.switches.addAll(scc)
    }

    currentSubproblem.finalSwitch = ptoi.last().value.first()
    subproblems.add(currentSubproblem)

    // Remove trivial subproblems
    subproblems.removeIf { it.switches.size <= 2 }

    val subCusps = subproblems.map { sp ->
        CUSPT(
            sp.initSwitch,
            sp.finalSwitch,
            cusp.initialRouting.filter { it.key != sp.finalSwitch && it.key in sp.switches },
            cusp.finalRouting.filter { it.key != sp.finalSwitch && it.key in sp.switches },
            dfaOf<Switch> { d ->
                val initialSwitchState = posDFAState[sp.initSwitch]!!.single()
                val finalSwitchState = posDFAState[sp.finalSwitch]!!.single()

                val oldToNewState = cusp.policy.states.associateWith {
                    d.state(initial = it == initialSwitchState, final = it == finalSwitchState)
                }

                cusp.policy.delta.forEach { (from, outgoing) ->
                    outgoing.forEach { (label, to) ->
                        oldToNewState[from]!!.edgeTo(oldToNewState[to]!!, label)
                    }
                }
            }
        )
    }

    return subCusps
}


fun switchPossibleNFAStates(cusp: CUSPT, pto: List<SCC>): Map<Switch, Set<DFAState>> {
    fun nextStates(ns: Set<DFAState>, s: Switch) =
        ns.map { cusp.policy[it, s] }

    val posNFAStates = cusp.allSwitches.associateWith { setOf<DFAState>() }.toMutableMap()

    posNFAStates[cusp.initialSwitch] = nextStates(setOf(cusp.policy.initial), cusp.initialSwitch).toSet()

    for (scc in pto) {
        for (i in 0 until scc.size - 1) {
            for (s in scc) {
                val nexts = (cusp.initialRouting[s] ?: setOf()) union (cusp.finalRouting[s] ?: setOf())

                nexts.filter { it in scc }.forEach {
                    posNFAStates[it] = posNFAStates[it]!! union nextStates(posNFAStates[s]!!, it)
                }
            }
        }

        for (s in scc) {
            val nexts = (cusp.initialRouting[s] ?: setOf()) union (cusp.finalRouting[s] ?: setOf())

            nexts.filter { it !in scc }.forEach {
                posNFAStates[it] = posNFAStates[it]!! union nextStates(posNFAStates[s]!!, it)
            }
        }
    }

    // Hack to make sure pseudo-final switch has DFA state
    posNFAStates[-2] = cusp.policy.finals

    return posNFAStates
}

fun partialTopologicalOrder(cusp: CUSPT): List<SCC> {
    data class NodeInfo(var index: Int, var lowlink: Int, var onStack: Boolean)

    var index = 0
    val stack = Stack<Int>()
    val info = cusp.allSwitches.associateWith { NodeInfo(-1, -1, false) }
    var scc = 0
    val sccs = mutableMapOf<Int, Int>() // Switch to scc

    val allEdges = (cusp.finalRouting.toList() + cusp.initialRouting.toList()).flatMap { (s, Rs) -> Rs.map { Edge(s, it) } }

    fun strongConnect(node: Int) {
        info[node]!!.index = index
        info[node]!!.lowlink = index
        index += 1
        stack.push(node)
        info[node]!!.onStack = true


        val nodeEdges = allEdges.filter { it.source == node }
        if (nodeEdges.isNotEmpty()) {
            nodeEdges.map { it.target }.forEach {
                if (info[it]!!.index == -1) {
                    strongConnect(it)
                    info[node]!!.lowlink = Integer.min(info[node]!!.lowlink, info[it]!!.lowlink)
                } else if (info[it]!!.onStack) {
                    info[node]!!.lowlink = Integer.min(info[node]!!.lowlink, info[it]!!.index)
                }
            }
        }

        if (info[node]!!.lowlink == info[node]!!.index) {
            scc++
            do {
                val w = stack.pop()
                info[w]!!.onStack = false
                sccs[w] = scc
            } while(node != w)
        }
    }

    strongConnect(cusp.initialSwitch)
    cusp.allSwitches.forEach {
        if (info[it]!!.index == -1) {
            strongConnect(it)
        }
    }

    return sccs.map { Pair(scc - it.value, it.key) }.groupBy { it.first }.toList().sortedBy { it.first }.map { it.second.map { it.second } }
}