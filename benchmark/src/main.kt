import java.nio.file.Path
import kotlin.io.path.pathString

data class OursFlip(val ours: Map<Measure, Any>, val flips: Map<Measure, Any>)

fun main(args: Array<String>) {

    val ours = handleResultsOurs(Path.of("../output/output/ours")).map { it.path.pathString to it.fields }.toMap()
    val flips = handleResultsFlip(Path.of("../output/output/flip")).map { it.path.pathString to it.fields }.toMap()

    val combined = ours.filter { it.key in flips }.map { (k, v) -> k to OursFlip(v, flips[k]!!) }.toMap()

    val solved = combined.filterValues { it.ours[Measure.Batches] != null && it.flips[Measure.Batches] != null }

    val better = solved.filter { it.value.flips[Measure.Batches] as Int > it.value.ours[Measure.Batches] as Int }
    val flipUsesTagAndMatchButWeDont = solved.filter { (it.value.flips[Measure.UsesTagAndMatch] ?: false) as Boolean }

    println("Total ${ours.entries.size}")
    println("Solved ${solved.size}")
    println("Better in ${better.size}")
    println("Flip uses TAM but we don't: ${flipUsesTagAndMatchButWeDont.size}")

    println(cactusPlotTime(combined))
    //val a = aggregateBy(res) { Path.of(it.path).parent.name }
}