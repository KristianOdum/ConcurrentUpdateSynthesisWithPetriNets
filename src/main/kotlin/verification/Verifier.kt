package verification
import java.io.File
import kotlin.math.ceil
import kotlin.math.roundToInt

class Verifier(val enginePath: String, val modelPath: String) {

    fun verifyQuery(queryPath: String): Boolean{
        val command = "./$enginePath $modelPath $queryPath -q 0 -r 0 -p"
        val pro = Runtime.getRuntime().exec(command)
        pro.waitFor()
        val output = pro.inputStream.readAllBytes().map { Char(it.toInt()) }.joinToString("")
        return output.contains("is satisfied")
    }
}

fun bisectionSearch(verifier: Verifier, queryPath: String, startingIndex: Int, upperBound: Int): Int {

    //Returns 0 if unsatisfiable
    var batches = 0
    var j = 1
    var i = startingIndex
    var k = upperBound

    var verified: Boolean
    var query = File(queryPath).readText()
    var tempFile = File("temp")
    while (true){
        query = query.replace("SWITCH_BATCHES <= [0-9]*".toRegex(), "SWITCH_BATCHES <= $i")
        tempFile.writeText(query)
        verified = verifier.verifyQuery(queryPath)

        if (verified){
            batches = i
            if(j == k){
                break
            }
            k = i-1
            i = ceil((i + j) / 2.0).roundToInt()
        }
        else{
            if(j == k){
                break
            }
            j = i+1
            i = ceil((i + k) / 2.0).roundToInt()
        }
    }
    if(tempFile.exists()){
        tempFile.delete()
    }
    return batches

}