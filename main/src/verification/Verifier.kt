package verification
import java.io.File
import java.nio.file.Path
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.system.measureTimeMillis

class Verifier(val enginePath: Path, val modelPath: Path) {
    fun verifyQuery(queryPath: String): Boolean {
        val command = "${enginePath.toAbsolutePath()} ${modelPath.toAbsolutePath()} $queryPath -q 0 -r 0 -p"
        val pro = Runtime.getRuntime().exec(command)
        pro.waitFor()
        val output = pro.inputStream.readAllBytes().map { Char(it.toInt()) }.joinToString("")
        return output.contains("is satisfied")
    }
}

fun bisectionSearch(verifier: Verifier, queryPath: Path, upperBound: Int) {
    print("Finding minimum required batches to satisfy the query")

    var batches = 0
    var j = 1
    var k = upperBound
    var i: Int

    var verified: Boolean
    var query = queryPath.toFile().readText()
    val tempQueryFile = kotlin.io.path.createTempFile("query").toFile()

    var time: Long
    while (true) {
        i = floor((j + k) / 2.0).roundToInt()
        query = query.replace("SWITCH_BATCHES <= [0-9]*".toRegex(), "SWITCH_BATCHES <= $i")
        tempQueryFile.writeText(query)

        time = measureTimeMillis {
            verified = verifier.verifyQuery("temp.q")
        }
        print("Verification ${if(verified) "succeeded" else "failed"} in ${time/1000.0} seconds with <= $i batches\n")

        if (verified) {
            batches = i
            if (j == k) break
            k = i - 1
        } else {
            if (j == k) break
            j = i + 1
        }
    }

    if (tempQueryFile.exists()) {
        tempQueryFile.delete()
    }

    if(batches == 0)
        print("Could not satisfy the query with any number of batches!")
    else
        print("Minimum $batches batches required to satisfy the query!")
}