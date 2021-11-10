import translate.NFA
import translate.UpdateSynthesisModel
import java.util.*

fun findTopologicalDecompositions(usm: UpdateSynthesisModel, nfa: NFA) {
    val pto = partialTopologicalOrder(usm)

    val sccNarrowness = mapOf<Int, Int>() // SCC to Narrowness

    for ((i, scc) in pto.withIndex()) {

    }
}

fun partialTopologicalOrder(usm: UpdateSynthesisModel): List<List<Int>> {
    data class NodeInfo(var index: Int, var lowlink: Int, var onStack: Boolean)

    var index = 0
    val stack = Stack<Int>()
    val info = usm.switches.associateWith { NodeInfo(-1, -1, false) }
    var scc = 0
    val sccs = mutableMapOf<Int, Int>() // Switch to scc

    val allEdges = usm.finalRouting + usm.initialRouting

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

    usm.switches.forEach {
        if (info[it]!!.index == -1) {
            strongConnect(it)
        }
    }

    return sccs.map { Pair(scc - it.value, it.key) }.groupBy { it.first }.toList().sortedBy { it.first }.map { it.second.map { it.second } }
}