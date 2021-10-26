import translate.*
import verification.Verifier
import verification.bisectionSearch
import kotlin.io.path.Path

fun printUsage() {
    println(
"""
conupsyn <verifypn_engine_path> <testJson> 
"""
    )
}

fun main(args: Array<String>) {
    val (enginePath, testJson) = try {
        Pair(
            Path(args[0]),
            Path(args[1])
        )
    } catch (e: Exception) {
        printUsage()
        throw e
    }

    val jsonText = testJson.toFile().readText()
    val usm = updateSynthesisModelFromJsonText(jsonText)
    val nfa = generateNFAFromUSMProperties(usm)
    nfa.toGraphviz("nfa")
    nfa.prune()
    nfa.toGraphviz("nfa_pruned")

    val (petriGame, queryPath) = generatePetriGameModelFromUpdateSynthesisNetwork(usm)
    addGraphicCoordinatesToPG(petriGame)
    val modelPath = kotlin.io.path.createTempFile("pnml_model")
    generatePnmlFileFromPetriGame(petriGame, modelPath)

    val verifier = Verifier(enginePath, modelPath)
    bisectionSearch(verifier, queryPath, usm.switches.size)
}