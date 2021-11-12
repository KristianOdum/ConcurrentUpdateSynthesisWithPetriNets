package synthetic

import kotlin.math.sqrt
import kotlin.random.Random

private data class Pos(val x: Int, val y: Int) {
    operator fun plus(other: Pos) = Pos(x + other.x, y + other.y)
}
private data class Vertex(val id: Int, val p: Pos)
private data class Edge(val from: Vertex, val to: Vertex, val weight: Int)

fun generateSyntheticProblem(switches: Int, edges: Int, ingress: Int, egress: Int) {
    val a = (sqrt(switches.toDouble()) / 2.0).toInt()
    val v = (0 until switches).map {
        Vertex(it, Pos(Random.nextInt(a), Random.nextInt(a)))
    }
    val k = v.groupBy { it.p }
    val j = k.map { (p, _) ->
        Pair(p, listOf(Pos(0,0), Pos(1,0), Pos(-1,0), Pos(0,1), Pos(0, -1)).map { it + p }
            .flatMap { k[it] ?: listOf() })
    }.toMap()

    val e = (0 until edges).map {
        val b = v.random()
        Edge(b, j[b.p]!!.random(), (1..10).random())
    }

    val ingressSwitches = (k[Pos(0,0)] ?: listOf()).shuffled().take(ingress)
    val egressSwitches = (k[Pos(a - 1,a - 1)] ?: listOf()).shuffled().take(egress)
}