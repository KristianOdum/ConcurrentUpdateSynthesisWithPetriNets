import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import translate.*
import verification.Verifier
import verification.bisectionSearch
import java.nio.file.Path
import kotlin.io.path.readText
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

fun runProblem() {
    var time: Long = measureTimeMillis {
        val jsonText = Options.testCase.readText()

        val usm = updateSynthesisModelFromJsonText(jsonText)

        val nfa: NFA
        val nfaPetri: PetriGame

        var time: Long = measureTimeMillis {
            nfa = generateNFAFromUSMProperties(usm)
            if (Options.drawGraphs) nfa.toGraphviz("nfa")
            nfa.prune()
            if (Options.drawGraphs) nfa.toGraphviz("nfa_pruned")
            if (Options.drawGraphs) outputPrettyNetwork(usm)
        }

        if(!Options.onlyNFAGen){
            outputPrettyNetwork(usm)

            //addGraphicCoordinatesToPG(petriGame)
            val modelPath = kotlin.io.path.createTempFile("pnml_model")

            println("Problem file: ${Options.testCase}")
            println("NFA generation time: ${time / 1000.0} seconds \nNFA states: ${nfa.states.size} \nNFA transitions: ${nfa.actions.size}")
            val (petriGame, queryPath, updateSwitchCount) = generatePetriGameModelFromUpdateSynthesisNetwork(usm, nfa)
            if (Options.debugPath != null) {
                generatePnmlFileFromPetriGame(petriGame.apply { addGraphicCoordinatesToPG(this) }, Path.of(Options.debugPath!! + "_model.pnml"))
                Path.of(Options.debugPath!! + "_query.q").toFile().writeText(queryPath.toFile().readText())
            }
            println("Petri game switches: ${usm.switches.size} \nPetri game updateable switches: ${updateSwitchCount}\nPetri game places: ${petriGame.places.size} \nPetri game transitions: ${petriGame.transitions.size}" +
                "\nPetri game arcs: ${petriGame.arcs.size}")

            val verifier: Verifier

            time = measureTimeMillis {
                verifier = Verifier(modelPath)
                bisectionSearch(verifier, queryPath, updateSwitchCount)
            }

            println("Total verification time: ${time / 1000.0} seconds")
        }
    }
    println("Total program runtime: ${time / 1000.0} seconds")
}

object Options {
    val argParser = ArgParser(programName="conupsyn")

    private val _enginePath by argParser.argument(ArgType.String, description = "Path to verifypn-games engine")
    val enginePath: Path by lazy { Path.of(_enginePath) }

    private val _testCase by argParser.argument(ArgType.String, fullName="test_case", description = "The test case to run on")
    val testCase: Path by lazy { Path.of(_testCase) }
    
    val drawGraphs by argParser.option(ArgType.Boolean, shortName = "g", description = "Draw graphs for various components").default(false)


    val onlyNFAGen by argParser.option(ArgType.Boolean, shortName = "onlynfa", description = "Only does the NFA translation, nothing more").default(false)

    val debugPath by argParser.option(ArgType.String, shortName = "d", fullName = "debugPrefix", description = "Output debugging files with the given prefix")

}

fun main(args: Array<String>) {
    Options.argParser.parse(args)

    runProblem()
}