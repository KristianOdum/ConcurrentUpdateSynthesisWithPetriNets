import translate.*
import verification.Verifier
import verification.bisectionSearch
import java.io.File
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.system.measureTimeMillis

fun printUsage() {
    println(
"""
conupsyn <verifypn_engine_path> <testJson> 
"""
    )
}

fun <T> timeFun(f: () -> T): Pair<T, Long> {
    var res: T? = null
    val time = measureTimeMillis {
        res = f()
    }
    return Pair(res!!, time)
}

fun runProblem(enginePath: Path, testCase: File) {
    val jsonText = testCase.readText()

    val usm = updateSynthesisModelFromJsonText(jsonText)
    println("Switches: ${usm.switches.size}")

    outputPrettyNetwork(usm)

    val nfa = generateNFAFromUSM(usm)

    println("Converting to PN model...")
    val (petriGame, queryPath) = generatePetriGameModelFromUpdateSynthesisNetwork(usm)
    println("Places: ${petriGame.places.size}")
    println("Transitions: ${petriGame.transitions.size}")
    println("Arcs: ${petriGame.arcs.size}")

    //addGraphicCoordinatesToPG(petriGame)
    val modelPath = kotlin.io.path.createTempFile("pnml_model")
    generatePnmlFileFromPetriGame(petriGame, modelPath)

    val verifier = Verifier(enginePath, modelPath)
    bisectionSearch(verifier, queryPath, usm.switches.size)
}

fun main(args: Array<String>) {

    val (enginePath, testJson) = try {
        Pair(
            Path(args[0]),
            Path(args[1])
        )
    } catch (e: Exception) {
        println("That didn't make any sense...")
        printUsage()
        throw e
    }

    runProblem(enginePath, testJson.toFile())
}