import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import kotlinx.cli.required
import translate.*
import verification.Verifier
import verification.bisectionSearch
import java.io.File
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.properties.Delegates
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty
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
    val jsonText = Options.testCase.readText()

    val usm = updateSynthesisModelFromJsonText(jsonText)
    println("Switches: ${usm.switches.size}")

    val nfa = generateNFAFromUSMProperties(usm)
    nfa.toGraphviz("nfa")
    nfa.prune()
    nfa.toGraphviz("nfa_pruned")
    outputPrettyNetwork(usm)

    println("Converting to PN model...")
    val (petriGame, queryPath, updateSwitchCount) = generatePetriGameModelFromUpdateSynthesisNetwork(usm)

    println("Places: ${petriGame.places.size}")
    println("Transitions: ${petriGame.transitions.size}")
    println("Arcs: ${petriGame.arcs.size}")

    //addGraphicCoordinatesToPG(petriGame)
    val modelPath = kotlin.io.path.createTempFile("pnml_model")
    generatePnmlFileFromPetriGame(petriGame, modelPath)

    val verifier = Verifier(modelPath)
    bisectionSearch(verifier, queryPath, modelPath, updateSwitchCount)
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