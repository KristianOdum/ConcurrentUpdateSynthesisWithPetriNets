import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.createDirectories
import kotlin.io.path.div

data class BenchmarkOptions(val mainJarPath:Path, val enginePath: Path, val outputDir: Path, val timeout: Long = 300L)

fun runTestcases(testCases: List<File>, options: BenchmarkOptions) {
    println("Running ${testCases.size} test cases")
    println("")
    runBlocking {
        val jobs = testCases.map { tc ->
            GlobalScope.launch {
                println("Launching ${tc.name}")

                assert(!tc.isAbsolute)
                val outPath = options.outputDir / Path.of(tc.parent) / (tc.nameWithoutExtension + "_results.txt" )
                outPath.createDirectories()

                val cmd = "java -jar ${options.mainJarPath.toAbsolutePath()} ${options.enginePath.toAbsolutePath()} ${tc.absolutePath} > $outPath"
                println(cmd)
                val p = Runtime.getRuntime().exec(cmd)

                // Wait for process to finish, if it did not terminate, mark as DNF
                if (p.waitFor(options.timeout, TimeUnit.SECONDS)) {
                    p.destroyForcibly()
                    println("Process ${tc.name} did not finish in time")
                } else {
                    println("Finished ${tc.name}")
                }
            }
        }

        jobs.joinAll()
    }

}