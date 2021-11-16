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

const val GRAPHICS_OUT = "main/graphics_out"

typealias Switch = Int

data class CUSP(
    val ingressSwitches: Set<Switch>,
    val egressSwitches: Set<Switch>,
    val initialRouting: Map<Switch, Set<Switch>>,
    val finalRouting: Map<Switch, Set<Switch>>,
    val policy: NFA,
) {
    val allSwitches: Set<Switch> = (initialRouting + finalRouting).entries.flatMap { setOf(it.key) + it.value }.toSet()
}

// Same as CUSP, but we have pseudonodes as initial and final that routes to set of initial and final switches, respectively.
data class CUSPT(
    val ingressSwitch: Switch,
    val egressSwitch: Switch,
    val initialRouting: Map<Switch, Set<Switch>>,
    val finalRouting: Map<Switch, Set<Switch>>,
    val policy: NFA,
) {
    val allSwitches: Set<Switch> = (initialRouting + finalRouting).entries.flatMap { setOf(it.key) + it.value }.toSet()

    override fun toString(): String {
        var res = "flow: $ingressSwitch to $egressSwitch\n"
        res += "Initial: "
        for ((from, to) in initialRouting) {
            res += "$from -> $to"
        }
        res += "Final: "
        for ((from, to) in finalRouting) {
            res += "$from -> $to"
        }
        return res
    }
}

fun generateCUSPFromUSM(usm: UpdateSynthesisModel, nfa: NFA) =
    CUSP(
        setOf(usm.reachability.initialNode),
        setOf(usm.reachability.finalNode),
        usm.switches.associateWith { s -> setOf((usm.initialRouting.find { it.source == s } ?: return@associateWith setOf<Switch>()).target) },
        usm.switches.associateWith { s -> setOf((usm.finalRouting.find { it.source == s } ?: return@associateWith setOf<Switch>()).target) },
        nfa
    )

fun generateCUSPTFromCUSP(cusp: CUSP) =
    CUSPT(
        -1,
        -2,
        cusp.initialRouting
                + mapOf(-1 to cusp.ingressSwitches)
                + cusp.egressSwitches.associateWith { setOf(-2) },
        cusp.finalRouting
                + mapOf(-1 to cusp.ingressSwitches)
                + cusp.egressSwitches.associateWith { setOf(-2) },
        cusp.policy,
    )

fun runProblem() {
    var time: Long = measureTimeMillis {
        val jsonText = Options.testCase.readText()

        val usm = updateSynthesisModelFromJsonText(jsonText)

        val nfa: NFA
        var time: Long = measureTimeMillis {
            nfa = generateNFAFromUSMProperties(usm)
            if (Options.drawGraphs) nfa.toGraphviz().toFile(File("$GRAPHICS_OUT/nfa.svg"))
            nfa.prune()
            if (Options.drawGraphs) nfa.toGraphviz().toFile(File("$GRAPHICS_OUT/nfa_pruned.svg"))
            if (Options.drawGraphs) outputPrettyNetwork(usm)
        }

        val cusp = generateCUSPFromUSM(usm, nfa)
        val cuspt = generateCUSPTFromCUSP(cusp)
        if (Options.drawGraphs) outputPrettyNetwork(usm)

        //addGraphicCoordinatesToPG(petriGame)
        val modelPath = kotlin.io.path.createTempFile("pnml_model")

        println("Problem file: ${Options.testCase}")
        println("NFA generation time: ${time / 1000.0} seconds \nNFA states: ${nfa.states.size} \nNFA transitions: ${nfa.actions.size}")
        val (petriGame, queryPath, updateSwitchCount) = generatePetriGameFromCUSPT(cuspt)
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
                    "\nPetri game arcs: ${petriGame.arcs.size}\nPetri game initial markings: ${petriGame.places.sumOf { it.initialTokens }}"
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
    val combinedWaypointNFA = genCombinedWaypointNFA(usm)
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
        shortName = "sb",
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