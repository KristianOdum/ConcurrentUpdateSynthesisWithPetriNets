import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import translate.*
import verification.Verifier
import verification.sequentialSearch
import java.io.File
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.system.measureTimeMillis

typealias Switch = Int

data class CUSP(
    val initialSwitches: Set<Switch>,
    val finalSwitches: Set<Switch>,
    val initialRouting: Map<Switch, Set<Switch>>,
    val finalRouting: Map<Switch, Set<Switch>>,
    val policy: DFA<Switch>,
) {
    val allSwitches: Set<Switch> = (initialRouting + finalRouting).entries.flatMap { setOf(it.key) + it.value }.toSet()
}

data class CUSPT(
    val initialSwitch: Switch,
    val finalSwitch: Switch,
    val initialRouting: Map<Switch, Set<Switch>>,
    val finalRouting: Map<Switch, Set<Switch>>,
    val policy: DFA<Switch>,
) {
    val allSwitches: Set<Switch> = (initialRouting + finalRouting).entries.flatMap { setOf(it.key) + it.value }.toSet()
}

fun generateCUSPFromUSM(usm: UpdateSynthesisModel, dfa: DFA<Switch>) =
    CUSP(
        setOf(usm.reachability.initialNode),
        setOf(usm.reachability.finalNode),
        usm.initialRouting.associate { Pair(it.source, setOf(it.target)) },
        usm.finalRouting.associate { Pair(it.source, setOf(it.target)) },
        dfa
    )

fun generateCUSPTFromCUSP(cusp: CUSP) =
    CUSPT(
        -1,
        -2,
        cusp.initialRouting + mapOf(-1 to cusp.initialSwitches),
        cusp.finalRouting + cusp.finalSwitches.associateWith { setOf(-2) },
        cusp.policy,
    )

fun runProblem() {
    var time: Long = measureTimeMillis {
        val jsonText = Options.testCase.readText()

        val usm = updateSynthesisModelFromJsonText(jsonText)

        val dfa: DFA<Switch>
        var time: Long = measureTimeMillis {
            dfa = generateNFAFromUSMProperties(usm)
        }
        if (Options.drawGraphs) dfa.toGraphviz().toFile(File("nfa.svg"))

        val cusp = generateCUSPTFromCUSP(generateCUSPFromUSM(usm, dfa))

        if (Options.drawGraphs) outputPrettyNetwork(usm).toFile(File("network.svg"))

        println("Problem file: ${Options.testCase}")
        println("NFA generation time: ${time / 1000.0} seconds \nNFA states: ${dfa.states.size} \nNFA transitions: ${dfa.delta.entries.sumOf { it.value.size }}")


        val subcusps: List<CUSPT>
        time = measureTimeMillis {
            subcusps = topologicalDecomposition(cusp)
        }
        println("Decomposed topology into ${subcusps.size} subproblems")
        println("Topological decomposition took ${time / 1000.0} seconds")

        //addGraphicCoordinatesToPG(petriGame)
        val modelPath = kotlin.io.path.createTempFile("pnml_model")

        val (petriGame, queryPath, updateSwitchCount) = generatePetriGameModelFromUpdateSynthesisNetwork(usm, dfa)
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
                    "\nPetri game arcs: ${petriGame.arcs.size}\nPetri game initial markings: ${petriGame.places.sumOf { it.initialMarkings }}"
        )

        val verifier: Verifier

        time = measureTimeMillis {
            verifier = Verifier(modelPath)
            sequentialSearch(verifier, queryPath, updateSwitchCount)
        }

        println("Total verification time: ${time / 1000.0} seconds")

    }
    println("Total program runtime: ${time / 1000.0} seconds")
}

fun generateNFA(){
    val jsonText = Options.testCase.readText()
    val usm = updateSynthesisModelFromJsonText(jsonText)
    val combinedWaypointNFA = genCombinedWaypointDFA(usm)
    combinedWaypointNFA.export(Options.onlyNFAGen!!)
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
        ArgType.String,
        shortName = "onlynfa",
        description = "Only does the NFA translation, nothing more"
    )

    val debugPath by argParser.option(
        ArgType.String,
        shortName = "d",
        fullName = "debugPrefix",
        description = "Output debugging files with the given prefix"
    )

    val maxSwicthesInBatch by argParser.option(
        ArgType.Int,
        shortName = "m",
        fullName = "switches_in_batch",
        description = "The maximum number of switches that can be in a batch. 0 = No limit"
    ).default(0)

    val outputVerifyPN by argParser.option(ArgType.Boolean, shortName = "P", description = "output the output from verifypn").default(false)
}

fun main(args: Array<String>) {
    Options.argParser.parse(args)
    if (Options.onlyNFAGen != null) generateNFA()
    else runProblem()
}