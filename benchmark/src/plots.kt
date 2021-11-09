

fun cactusPlotTime(d: Map<String, OursFlip>): String {
    val ours = d.values.map { it.ours[Measure.TotalTime] }.filterNotNull().map { it as Double }
    val flip = d.values.map { it.flips[Measure.TotalTime] }.filterNotNull().map { it as Double }

    return listOf(ours, flip).map { it.sorted().withIndex() }.map { it.joinToString("") { "(${it.index},${it.value})" } }.joinToString("\n")
}