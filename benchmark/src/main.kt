import java.nio.file.Path
import kotlin.io.path.pathString

data class OursFlip(val ours: Map<Measure, Any>, val flips: Map<Measure, Any>)
data class OursFlipWithNull(val ours: Map<Measure, Any>?, val flips: Map<Measure, Any>?)

fun main(args: Array<String>) {
    val ours = handleResultsOurs(Path.of("../output/ours")).map { it.path.pathString to it.fields }.toMap().filter { it.key.contains("synthethic") }
    val flips = handleResultsFlip(Path.of("../output/flip")).map { it.path.pathString to it.fields }.toMap().filter { it.key.contains("synthethic") }

    val combined = ours.filter { it.key in flips }.map { (k, v) -> k to OursFlip(v, flips[k]!!) }.toMap()
    val combinedWithNulls = (ours.keys + flips.keys).map { it to OursFlipWithNull(ours[it], flips[it]) }.toMap()

    val solved = combined.filterValues { it.ours[Measure.Batches] != null && it.flips[Measure.Batches] != null }

    val better = solved.filter { it.value.flips[Measure.Batches] as Int > it.value.ours[Measure.Batches] as Int }
    val flipUsesTagAndMatchButWeDont = solved.filter { (it.value.flips[Measure.UsesTagAndMatch] ?: false) as Boolean }

    println("Total ${ours.entries.size} ${flips.entries.size}")
    println("Solved ${solved.size}")
    println("Better in ${better.size}")
    println("Flip uses TAM ${flips.values.filter { it[Measure.UsesTagAndMatch] as Boolean }.size}")
    println("Flip uses TAM but we don't: ${flipUsesTagAndMatchButWeDont.size}")
    println("Flip model too large: ${flips.values.count { it[Measure.ModelTooBig] == true }}")

    println(cactusPlotTime(combinedWithNulls))

    println(ours.entries.maxByOrNull { it.value[Measure.TotalTime] as Double } )
    //val a = aggregateBy(res) { Path.of(it.path).parent.name }
}