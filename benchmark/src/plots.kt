
fun cactusPlotTimeString(d: Map<String, Map<String, Map<Measure, Any>>>) =
    cactusPlotTime(d).entries.joinToString("\n") { "${it.key}:\n" + it.value.withIndex().joinToString("") { (i, v) -> "($i, $v)" } }

fun cactusPlotTime(d: Map<String, Map<String, Map<Measure, Any>>>) =
    d.entries.first().value.keys.map { m ->
        m to d.values.map { it[m]!![Measure.TotalTime] }.filterNotNull().map { it as Double }.sorted()
    }.toMap()