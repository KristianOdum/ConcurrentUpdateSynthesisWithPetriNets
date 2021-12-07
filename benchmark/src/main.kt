import java.nio.file.Path
import kotlin.io.path.pathString

data class OursFlip(val ours: Map<Measure, Any>, val flips: Map<Measure, Any>)
data class OursFlipWithNull(val ours: Map<Measure, Any>?, val flips: Map<Measure, Any>?)

fun main(args: Array<String>) {
    val createNewZoo = false
    if (createNewZoo) {
        addRandomWaypointsToNetworks(7, Path.of("""artefact/data/zoo_json"""), 0)
        return
    }
    val filter = "zoo_json_plus1"

    val ours = handleResultsOurs(Path.of("../output/ours")).map { it.path.pathString to it.fields }.toMap().filter { it.key.contains(filter) }
    //val ours_chain= handleResultsOurs(Path.of("../output/td")).map { it.path.pathString to it.fields }.toMap().filter { it.key.contains(filter) }
    val ours_if = handleResultsOurs(Path.of("../output/if")).map { it.path.pathString to it.fields }.toMap().filter { it.key.contains(filter) }
    val ours_td = handleResultsOurs(Path.of("../output/td")).map { it.path.pathString to it.fields }.toMap().filter { it.key.contains(filter) }
    val ours_nochain = handleResultsOurs(Path.of("../output/nochain")).map { it.path.pathString to it.fields }.toMap().filter { it.key.contains(filter) }
    val ours_none = handleResultsOurs(Path.of("../output/none")).map { it.path.pathString to it.fields }.toMap().filter { it.key.contains(filter) }


    val flips = handleResultsFlip(Path.of("../output/flip")).map { it.path.pathString to it.fields }.toMap().filter { it.key.contains(filter) }
    //val parakeet = handleResultsParakeet(Path.of("../output/parakeet")).map { it.path.pathString to it.fields }.toMap().filter { it.key.contains(filter) }

    val combined = ours.filter { it.key in flips }.map { (k, v) -> k to mapOf("ours" to v, "flip" to flips[k]!!) }.toMap()

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

    val oursCombined = ours.filter { it.key in ours }
        .map { (k,v) -> k to mapOf("ours" to v, "ours_if" to ours_if[k]!!, "ours_td" to ours_td[k]!!, "ours_none" to ours_none[k]!!) }.toMap()
    val chaincomp = ours.filter { it.key in ours }
        .map { (k,v) -> k to mapOf("ours" to v, "ours_nochain" to ours_nochain[k]!!) }.toMap()

    println()
    println(chaincomp.filter { it.value["ours"]!![Measure.Batches] != it.value["ours_nochain"]!![Measure.Batches] })

    println()
    println(cactusPlotTimeString(oursCombined))

    println(ours.entries.maxByOrNull { (it.value[Measure.TotalTime] ?: 0.0) as Double } )
    println(combined.entries.maxByOrNull { (it.value["flip"]!![Measure.Batches]) as Int } )
    //val a = aggregateBy(res) { Path.of(it.path).parent.name }

}