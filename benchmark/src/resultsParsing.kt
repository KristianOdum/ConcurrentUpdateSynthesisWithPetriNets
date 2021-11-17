import java.nio.file.Path

enum class Measure { TotalTime, Batches, UpdatableSwitches, UsesTagAndMatch, ModelTooBig }

fun <T,V> Map<T,V?>.filterValuesNotNull(p: (V) -> Boolean = { true }): Map<T,V> =
    filterValues { it != null && p(it) }.map { it.key to it.value!! }.toMap()

data class TestData(val path: Path, val fields: Map<Measure, Any>)
data class AggregatedData(val name: String, val fields: Map<Measure, List<Any>>)

fun Regex.firstGroup(s: String): String? {
    val mr = find(s)
    return (mr ?: return null ).groupValues[1]
}

fun handleResultsOurs(outputDir: Path): List<TestData> =
    parseResults(outputDir, listOf(
        { s -> Measure.UpdatableSwitches to """Petri game updateable switches: (\d+)""".toRegex().firstGroup(s)?.toInt() },
        { s -> Measure.Batches to """Minimum batches required: (\d+)""".toRegex().firstGroup(s)?.toInt()},
        { s -> Measure.TotalTime to """Total program runtime: +(\d+\.\d+) +seconds""".toRegex().firstGroup(s)?.toDouble()}
    ))

fun handleResultsFlip(outputDir: Path): List<TestData> =
    parseResults(outputDir, listOf(
        { s -> Measure.Batches to """\*STEP (\d+)\*""".toRegex().findAll(s).lastOrNull()?.groupValues?.component2()?.toInt() },
        { s -> Measure.TotalTime to """Finished in +(\d+\.\d+) +seconds""".toRegex().firstGroup(s)?.toDouble()},
        { s -> Measure.UsesTagAndMatch to ("""add-tag""".toRegex().containsMatchIn(s)) },
        { s -> Measure.ModelTooBig to s.contains("""Model too large for size-limited license""")}
    ))

fun parseResults(outputDir: Path, propertyParsers: List<(String) -> Pair<Measure, Any?>>) =
    outputDir.toFile().walk().filter { it.isFile && it.extension == "txt" }.map { tcOut ->
        val raw = tcOut.readText()
        TestData(Path.of(tcOut.relativeTo(outputDir.toFile()).path), propertyParsers.associate { it(raw) }.toMap().filterValuesNotNull())
    }.toList()

fun aggregateBy(tds: List<TestData>, e: (TestData) -> String) =
    tds.groupBy { e(it) }
        .map { (s, ts) ->
            AggregatedData(s, ts.flatMap { it.fields.entries }.groupBy { it.key }.map { (s, l) -> s to l.map { it.value } }.toMap())
        }