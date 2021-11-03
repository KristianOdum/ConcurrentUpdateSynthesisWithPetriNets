import com.xenomachina.argparser.ArgParser
import java.io.File
import java.nio.file.Path
import kotlin.io.path.createDirectories

fun printUsage() {
    println(
"""
benchmarks <engine_path> <tests_dir> [output_dir] [main_jar_path]
"""
    )
}

fun main(args: Array<String>) {

    val (enginePath, testsRootDir, outputDir, mainJarPath) = try {
        listOf(
            Path.of(args[0]),
            Path.of(args[1]),
            Path.of(args.getOrNull(2) ?: "output"),
            Path.of(args.getOrNull(3) ?: "main.jar")
        )
    } catch (e: Exception) {
        println("Whoopsie daisy, you wrote something crazy!")
        printUsage()
        throw e
    }

    outputDir.createDirectories()

    val testCases = testsRootDir.toFile().walk().filter { it.extension == "json" }

    runTestcases(testCases.toList(), BenchmarkOptions(mainJarPath, enginePath, outputDir))

}