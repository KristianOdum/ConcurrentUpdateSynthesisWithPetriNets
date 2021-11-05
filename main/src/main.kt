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
import kotlin.system.measureTimeMillis

fun runProblem() {
    var time: Long = measureTimeMillis {
        val jsonText = Options.testCase.readText()

        val usm = updateSynthesisModelFromJsonText(jsonText)

        val nfa: NFA
        var time: Long = measureTimeMillis {
            nfa = generateNFAFromUSMProperties(usm)
            if (Options.drawGraphs) nfa.toGraphviz().toFile(File("nfa.svg"))
            nfa.prune()
            if (Options.drawGraphs) nfa.toGraphviz().toFile(File("nfa_pruned.svg"))
            if (Options.drawGraphs) outputPrettyNetwork(usm)
        }

        if (Options.drawGraphs) outputPrettyNetwork(usm)

        //addGraphicCoordinatesToPG(petriGame)
        val modelPath = kotlin.io.path.createTempFile("pnml_model")

        println("Problem file: ${Options.testCase}")
        println("NFA generation time: ${time / 1000.0} seconds \nNFA states: ${nfa.states.size} \nNFA transitions: ${nfa.actions.size}")
        val (petriGame, queryPath, updateSwitchCount) = generatePetriGameModelFromUpdateSynthesisNetwork(usm, nfa)
        if (Options.debugPath != null) {
            generatePnmlFileFromPetriGame(
                petriGame.apply { addGraphicCoordinatesToPG(this) },
                Path.of(Options.debugPath!! + "_model.pnml")
            )
            Path.of(Options.debugPath!! + "_query.q").toFile().writeText(queryPath.toFile().readText())
        }
        generatePnmlFileFromPetriGame(petriGame, modelPath)
        println(
            "Petri game switches: ${usm.switches.size} \nPetri game updateable switches: ${updateSwitchCount}\nPetri game places: ${petriGame.places.size} \nPetri game transitions: ${petriGame.transitions.size}" +
                    "\nPetri game arcs: ${petriGame.arcs.size}"
        )

        val verifier: Verifier

        time = measureTimeMillis {
            verifier = Verifier(modelPath)
            bisectionSearch(verifier, queryPath, updateSwitchCount)
        }

        println("Total verification time: ${time / 1000.0} seconds")

    }
    println("Total program runtime: ${time / 1000.0} seconds")
}

fun generateNFA(){
    val jsonText = Options.testCase.readText()
    val usm = updateSynthesisModelFromJsonText(jsonText)
    val combinedWaypointNFA = genCombinedWaypointNFA(usm)
    combinedWaypointNFA.export("WaypointsNFA")
    println("Waypoint NFA successfully generated!")
}

object Options {
    val argParser = ArgParser(programName = "conupsyn")

    private val _enginePath by argParser.argument(ArgType.String, description = "Path to verifypn-games engine")
    val enginePath: Path by lazy { Path.of(_enginePath) }

    private val _testCase by argParser.argument(
        ArgType.String,
        fullName = "test_case",
        description = "The test case to run on"
    )
    val testCase: Path by lazy { Path.of(_testCase) }

    val drawGraphs by argParser.option(
        ArgType.Boolean,
        shortName = "g",
        description = "Draw graphs for various components"
    ).default(false)


    val onlyNFAGen by argParser.option(
        ArgType.Boolean,
        shortName = "onlynfa",
        description = "Only does the NFA translation, nothing more"
    ).default(false)

    val debugPath by argParser.option(
        ArgType.String,
        shortName = "d",
        fullName = "debugPrefix",
        description = "Output debugging files with the given prefix"
    )

    val outputVerifyPN by argParser.option(ArgType.Boolean, shortName = "P", description = "output the output from verifypn").default(false)
}

fun main(args: Array<String>) {
    Options.argParser.parse(args)
    if(Options.onlyNFAGen) generateNFA()
    else runProblem()
}