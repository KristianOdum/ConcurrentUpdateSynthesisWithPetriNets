package verification
import java.io.File
import java.io.PrintWriter
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.*
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.system.measureTimeMillis

class Verifier(val modelPath: Path) {
    fun verifyQuery(queryPath: String): Boolean {
        val command = "${Options.enginePath.toAbsolutePath()} ${modelPath.toAbsolutePath()} $queryPath -q 0 -r 0 -p"
        val pro = Runtime.getRuntime().exec(command)
        pro.waitFor()
        val output = pro.inputStream.readAllBytes().map { Char(it.toInt()) }.joinToString("")
        return output.contains("is satisfied")
    }
}

fun bisectionSearch(verifier: Verifier, queryPath: Path, modelPath: Path, upperBound: Int) {
    print("Finding minimum required batches to satisfy the query\n")

    var batches = 0
    var start = 1
    var end = upperBound
    var mid: Int

    var verified: Boolean
    //val query = queryPath.toFile().readText()
    //val tempQueryFile = kotlin.io.path.createTempFile("query").toFile()
    //tempQueryFile.writeText(query)

    var time: Long
    while (start <= end) {
        mid = floor((start + end) / 2.0).roundToInt()
        //query = query.replace("UPDATE_P_BATCHES <= [0-9]*".toRegex(), "UPDATE_P_BATCHES <= $mid")

        val regex = """UPDATE_P_BATCHES\s*</text>\s*</name>\s*<initialMarking>\s*<text>(\d+)""".toRegex()
        val text = regex.replace(modelPath.readText()){
            m -> "UPDATE_P_BATCHES\n\t\t\t\t\t</text>\n\t\t\t\t</name>\n\t\t\t\t<initialMarking>\n\t\t\t\t\t<text>${mid}"
        }
        modelPath.writeText(text)

        time = measureTimeMillis {
            verified = verifier.verifyQuery(queryPath.pathString)
        }
        print("Verification ${if(verified) "succeeded" else "failed"} in ${time/1000.0} seconds with <= ${mid} batches\n")

        if (verified) {
            batches = mid
            end = mid - 1
        } else {
            start = mid + 1
        }
    }

    if(batches == 0)
        print("Could not satisfy the query with any number of batches!")
    else
        print("Minimum ${batches} batches required to satisfy the query!")
}