package verification

import Batch
import Options
import println
import java.nio.file.Path
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.system.measureTimeMillis

class Verifier(val modelPath: Path) {
    fun verifyQuery(queryPath: String): Pair<Boolean, String?> {
        val command = "${Options.enginePath.toAbsolutePath()} --strategy-output _ ${modelPath.toAbsolutePath()} $queryPath -q 0 -r 0"
        if (Options.outputVerifyPN)
            v.High.println(command)
        val pro = Runtime.getRuntime().exec(command)
        pro.waitFor()
        val output = pro.inputStream.readAllBytes().map { Char(it.toInt()) }.joinToString("")
        if (Options.outputVerifyPN)
            v.High.println(output)

        return if (output.contains("is satisfied")) {
            val from = output.lastIndexOf("##BEGIN STRATEGY##")
            val to = output.indexOf("##END STRATEGY##")
            Pair(true, output.substring(from, to))
        } else if (output.contains("is NOT satisfied"))
            Pair(false, null)
        else {
            v.Low.println(pro.errorStream.readAllBytes().map { Char(it.toInt()) }.joinToString(""))
            throw OutOfMemoryError("VerifyPN ran out of memory")
        }
    }
}

fun sequentialSearch(verifier: Verifier, queryPath: Path, upperBound: Int): List<Batch>? {
    var case: Int

    var verified: Boolean
    var strategy: String? = null
    var query = queryPath.toFile().readText()
    val tempQueryFile = kotlin.io.path.createTempFile("query").toFile()
    var time: Long

    // Test with max amount of batches
    query = query.replace("UPDATE_P_BATCHES <= [0-9]*".toRegex(), "UPDATE_P_BATCHES <= $upperBound")
    tempQueryFile.writeText(query)
    time = measureTimeMillis {
        val (v, s) = verifier.verifyQuery(tempQueryFile.path)
        verified = v
        if (v)
            strategy = s
    }
    v.High.println("Verification ${if (verified) "succeeded" else "failed"} in ${time / 1000.0} seconds with <= $upperBound batches")

    if (verified) {
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
                val (v, s) = verifier.verifyQuery(tempQueryFile.path)
                verified = v
                if (v)
                    strategy = s
            }

            v.High.println("Verification ${if (verified) "succeeded" else "failed"} in ${time / 1000.0} seconds with <= $case batches\n")

            if (verified) {
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
                    val (v, s) = verifier.verifyQuery(tempQueryFile.path)
                    verified = v
                    if (v)
                        strategy = s
                }

                v.High.println("Verification ${if (verified) "succeeded" else "failed"} in ${time / 1000.0} seconds with <= $case batches")

                if (verified) {
                    case -= 1
                }
                if (!verified) {
                    break
                }
            }
        }
    }

    return if (strategy != null) getUpdateBatchesFromStrategy(strategy!!) else null
}