package verification

import Options
import java.nio.file.Path
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

fun bisectionSearch(verifier: Verifier, queryPath: Path, upperBound: Int) {
    println()
    print("Finding minimum required batches to satisfy the query\n")

    var batches = 0
    var start = 1
    var end = upperBound
    var mid: Int
    var flag: Boolean = false

    var verified: Boolean
    var query = queryPath.toFile().readText()
    val tempQueryFile = kotlin.io.path.createTempFile("query").toFile()

    var time: Long

    // Used to check if it is at all possible so update with max number of batches
    mid = end

    while (start <= end) {
        query = query.replace("UPDATE_P_BATCHES <= [0-9]*".toRegex(), "UPDATE_P_BATCHES <= $mid")

        tempQueryFile.writeText(query)

        time = measureTimeMillis {
            verified = verifier.verifyQuery(tempQueryFile.path)
        }

        print("Verification ${if (verified) "succeeded" else "failed"} in ${time / 1000.0} seconds with <= $mid batches\n")

        if (verified) {
            batches = mid
            end = mid - 1
        } else {
            start = mid + 1
        }

        // Goes sequentially down from 5 batches
        if (!flag) {
            if(end < 5){
                mid = end
            }
            else{
                mid = 5
            }
            flag = true
        } else {
            mid -= 1
        }
    }

    println()

    if (batches == 0)
        print("Could not satisfy the query with any number of batches!")
    else
        print("Minimum $batches batches required to satisfy the query!")
}