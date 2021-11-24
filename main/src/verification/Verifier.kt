package verification

import Options
import println
import java.nio.file.Path
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.system.measureTimeMillis

class Verifier(val modelPath: Path) {
    fun verifyQuery(queryPath: String): Boolean {
        val command = "${Options.enginePath.toAbsolutePath()} ${modelPath.toAbsolutePath()} $queryPath -q 0 -r 0"
        if (Options.outputVerifyPN)
            v.High.println(command)
        val pro = Runtime.getRuntime().exec(command)
        pro.waitFor()
        val output = pro.inputStream.readAllBytes().map { Char(it.toInt()) }.joinToString("")
        if (Options.outputVerifyPN)
            v.High.println(output)

        return if (output.contains("is satisfied"))
            true
        else if (output.contains("is NOT satisfied"))
            false
        else {
            v.Low.println(pro.errorStream.readAllBytes().map { Char(it.toInt()) }.joinToString(""))
            throw Exception()
        }
    }
}

fun sequentialSearch(verifier: Verifier, queryPath: Path, upperBound: Int): Int {
    var batches = Int.MAX_VALUE
    var case: Int

    var verified: Boolean
    var query = queryPath.toFile().readText()
    val tempQueryFile = kotlin.io.path.createTempFile("query").toFile()
    var time: Long

    // Test with max amount of batches
    query = query.replace("UPDATE_P_BATCHES <= [0-9]*".toRegex(), "UPDATE_P_BATCHES <= $upperBound")
    tempQueryFile.writeText(query)
    time = measureTimeMillis {
        verified = verifier.verifyQuery(tempQueryFile.path)
    }
    v.High.println("Verification ${if (verified) "succeeded" else "failed"} in ${time / 1000.0} seconds with <= $upperBound batches")

    if (verified) {
        batches = upperBound

        if (upperBound > 5) {
            case = 5
        } else if (upperBound == 5) {
            case = 4
        } else {
            case = upperBound - 1
        }
        // Test with 5 or less
        while (case > 0) {
            query = query.replace("UPDATE_P_BATCHES <= [0-9]*".toRegex(), "UPDATE_P_BATCHES <= $case")

            tempQueryFile.writeText(query)

            time = measureTimeMillis {
                verified = verifier.verifyQuery(tempQueryFile.path)
            }

            v.High.println("Verification ${if (verified) "succeeded" else "failed"} in ${time / 1000.0} seconds with <= $case batches\n")

            if (verified) {
                batches = case
                case -= 1
            } else if (!verified and (case == 5)) {
                case = upperBound - 1
                break
            } else if (!verified) {
                break
            }
        }
        //Test sequentially down from max batches
        if (!verified) {
            while (case > 5) {
                query = query.replace("UPDATE_P_BATCHES <= [0-9]*".toRegex(), "UPDATE_P_BATCHES <= $case")

                tempQueryFile.writeText(query)

                time = measureTimeMillis {
                    verified = verifier.verifyQuery(tempQueryFile.path)
                }

                v.High.println("Verification ${if (verified) "succeeded" else "failed"} in ${time / 1000.0} seconds with <= $case batches")

                if (verified) {
                    batches = case
                    case -= 1
                }
                if (!verified) {
                    break
                }
            }
        }
    }

    return batches
}