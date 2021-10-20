package verification
import java.io.File
import kotlin.math.ceil
import kotlin.math.roundToInt

class Verifier(val enginePath: String, val modelPath: String) {
    fun verifyQuery(queryPath: String): Boolean {
        val command = "./$enginePath $modelPath $queryPath -q 0 -r 0 -p"
        val pro = Runtime.getRuntime().exec(command)
        pro.waitFor()
        val output = pro.inputStream.readAllBytes().map { Char(it.toInt()) }.joinToString("")
        return output.contains("is satisfied")
    }
}

fun bisectionSearch(verifier: Verifier, queryPath: String, upperBound: Int): Int {
    //Returns 0 if unsatisfiable
    var batches = 0
    var j = 1
    var k = upperBound
    var i: Int

    var verified: Boolean
    var query = File(queryPath).readText()
    var tempFile = File("temp.q")

    while (true) {
        i = ceil((j + k) / 2.0).roundToInt()
        query = query.replace("SWITCH_BATCHES <= [0-9]*".toRegex(), "SWITCH_BATCHES <= $i")
        tempFile.writeText(query)
        verified = verifier.verifyQuery("temp.q")


        if (verified) {
            batches = i
            if (j == k) {
                break
            }
            k = i - 1
        }
        else {
            if (j == k) {
                break
            }
            j = i + 1
        }
    }
    if (tempFile.exists()) {
        tempFile.delete()
    }

    return batches
}