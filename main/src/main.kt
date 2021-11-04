import Options.drawGraphs
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import kotlinx.cli.required
import translate.*
import verification.Verifier
import verification.bisectionSearch
import java.nio.file.Path
import kotlin.io.path.Path
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

        outputPrettyNetwork(usm)

        println("Problem file: ${Options.testCase}")
        println("NFA generation time: ${time / 1000.0} seconds \nNFA states: ${nfa.states.size} \nNFA transitions: ${nfa.actions.size}")
        val (petriGame, queryPath) = generatePetriGameModelFromUpdateSynthesisNetwork(usm, nfa)
        generatePnmlFileFromPetriGame(petriGame.apply { addGraphicCoordinatesToPG(this) }, Path.of("petriwithnfa.pnml"))
        println("Petri game switches: ${usm.switches.size} \nPetri game places: ${petriGame.places.size} \nPetri game transitions: ${petriGame.transitions.size}" +
                "\nPetri game arcs: ${petriGame.arcs.size}")

        //addGraphicCoordinatesToPG(petriGame)
        val modelPath = kotlin.io.path.createTempFile("pnml_model")
        generatePnmlFileFromPetriGame(petriGame, modelPath)

        val verifier: Verifier

        time = measureTimeMillis {
            verifier = Verifier(modelPath)
            bisectionSearch(verifier, queryPath, usm.switches.size)
        }

        println("Total verification time: ${time / 1000.0} seconds")
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
}

fun main(args: Array<String>) {
    Options.argParser.parse(args)

    runProblem()
}