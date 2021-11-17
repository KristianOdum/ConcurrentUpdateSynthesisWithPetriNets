

fun cactusPlotTime(d: Map<String, OursFlipWithNull>): String {
    val ours = d.values.filter { it.ours?.get(Measure.TotalTime) != null }.map { it.ours?.get(Measure.TotalTime) }.filterNotNull().map { it as Double }
    val flip = d.values.filter { !((it.flips?.get(Measure.UsesTagAndMatch) ?: false) as Boolean) }.map { it.flips?.get(Measure.TotalTime) }.filterNotNull().map { it as Double }

    return listOf(ours, flip).map { it.sorted().withIndex() }.joinToString("\n") { it.joinToString("") { "(${it.index},${it.value})" } }
}