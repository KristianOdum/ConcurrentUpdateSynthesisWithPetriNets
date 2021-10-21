import java.io.File
import java.nio.file.Path
import kotlin.io.path.createDirectories

fun printUsage() {
    println(
        """
benchmarks <tests_dir> [output_dir]
"""
    )
}

fun main(args: Array<String>) {
    val (testsRootDir, outputDir) = try {
        Pair(
            Path.of(args.getOrNull(1) ?: throw IllegalArgumentException()),
            Path.of(args.getOrNull(2) ?: "output")
        )
    } catch (e: Exception) {
        println("Whoopsie daisy, you wrote something crazy!")
        printUsage()
        throw e
    }

    outputDir.createDirectories()

    File(testsRootDir.toAbsolutePath().toString()).walk().filter { it.extension == ".json" }.forEach { println(it) }
}