import java.nio.file.Path
import kotlin.io.path.pathString

data class OursFlip(val ours: Map<Measure, Any>, val flips: Map<Measure, Any>)
data class OursFlipWithNull(val ours: Map<Measure, Any>?, val flips: Map<Measure, Any>?)

fun main(args: Array<String>) {
    val filter = "zoo_json"

    val ours = handleResultsOurs(Path.of("../new_output/all")).map { it.path.pathString to it.fields }.toMap().filter { it.key.contains(filter) }
    val ours_no_eq_no_td = handleResultsOurs(Path.of("../new_output/no_eq_no_td")).map { it.path.pathString to it.fields }.toMap().filter { it.key.contains(filter) }
    val ours_no_td = handleResultsOurs(Path.of("../new_output/no_td")).map { it.path.pathString to it.fields }.toMap().filter { it.key.contains(filter) }

    val flips = handleResultsFlip(Path.of("../new_output/flip")).map { it.path.pathString to it.fields }.toMap().filter { it.key.contains(filter) }
    val parakeet = handleResultsParakeet(Path.of("../output/parakeet")).map { it.path.pathString to it.fields }.toMap().filter { it.key.contains(filter) }

    val combined = ours.filter { it.key in flips }.map { (k, v) -> k to mapOf("ours" to v, "flip" to flips[k]!!) }.toMap()
    val oursCombined = ours.filter { it.key in ours }
        .map { (k,v) -> k to mapOf("ours" to v, "ours_no_eq_no_td" to ours_no_eq_no_td[k]!!, "ours_no_td" to ours_no_td[k]!!) }.toMap()

    val solved = combined.filterValues { it["ours"]!![Measure.Batches] != null && it["flip"]!![Measure.Batches] != null }

    val better = solved.filter { it.value["flip"]!![Measure.Batches] as Int > it.value["ours"]!![Measure.Batches] as Int }
    val flipUsesTagAndMatchButWeDont = solved.filter { (it.value["flip"]!![Measure.UsesTagAndMatch] ?: false) as Boolean }

    println("Total ${ours.entries.size} ${flips.entries.size}")
    println("Solved ${solved.size}")
    println("Better in ${better.size}")
    println("FLIP better in ${solved.filter { it.value["ours"]!![Measure.Batches] as Int > it.value["flip"]!![Measure.Batches] as Int }.size}")
    println("Flip uses TAM ${flips.values.filter { it[Measure.UsesTagAndMatch] as Boolean }.size}")
    println("Flip uses TAM but we don't: ${flipUsesTagAndMatchButWeDont.size}")
    println("Flip model too large: ${flips.values.count { it[Measure.ModelTooBig] == true }}")

    println()
    println(cactusPlotTimeString(combined))

    println()
    println(cactusPlotTimeString(oursCombined))

    println(ours.entries.maxByOrNull { (it.value[Measure.TotalTime] ?: 0.0) as Double } )
    println(combined.entries.maxByOrNull { (it.value["flip"]!![Measure.Batches]) as Int } )
    //val a = aggregateBy(res) { Path.of(it.path).parent.name }
}