package verification

import Options
import java.nio.file.Path
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.system.measureTimeMillis

class Verifier(val modelPath: Path) {
    fun verifyQuery(queryPath: String): Boolean {
        val command = "${Options.enginePath.toAbsolutePath()} ${modelPath.toAbsolutePath()} $queryPath -q 0 -r 0 -p"
        if (Options.outputVerifyPN)
            println(command)
        val pro = Runtime.getRuntime().exec(command)
        pro.waitFor()
        val output = pro.inputStream.readAllBytes().map { Char(it.toInt()) }.joinToString("")
        if (Options.outputVerifyPN)
            println(output)

        return if (output.contains("is satisfied"))
            true
        else if (output.contains("is NOT satisfied"))
            false
        else
            throw Exception(pro.errorStream.readAllBytes().map { Char(it.toInt()) }.joinToString(""))
    }
}

fun bisectionSearch(verifier: Verifier, queryPath: Path, upperBound: Int) {
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

        if((mid == 5) and (verified == false)){
            flag = false
            break
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

    end = upperBound

    // If verification succeeds with max batches but not with 5 or lower, proceed with binary search
    if(!flag) {
        while (start <= end) {
            mid = floor((start + end) / 2.0).roundToInt()
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
        }
    }

    if (batches == 0)
        println("Could not satisfy the query with any number of batches!")
    else
        println("Minimum $batches batches required to satisfy the query!")
}