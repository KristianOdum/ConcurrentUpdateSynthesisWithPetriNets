import java.nio.file.Path

fun parseResults(outputDir: Path, propertyParsers: List<(String) -> Pair<String, Any?>>) =
    outputDir.toFile().walk().map { tcOut ->
        val raw = tcOut.readText()
        Pair(tcOut.path, propertyParsers.associate { it(raw) })
    }.toList()

