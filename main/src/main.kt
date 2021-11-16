import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import translate.*
import verification.Verifier
import verification.sequentialSearch
import java.io.File
import java.lang.Integer.max
import java.lang.Integer.min
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.system.measureTimeMillis

const val GRAPHICS_OUT = "main/graphics_out"

typealias Switch = Int

data class CUSP(
    val ingressSwitches: Set<Switch>,
    val egressSwitches: Set<Switch>,
    val initialRouting: Map<Switch, Set<Switch>>,
    val finalRouting: Map<Switch, Set<Switch>>,
    val policy: DFA<Switch>,
) {
    val allSwitches: Set<Switch> = (initialRouting + finalRouting).entries.flatMap { setOf(it.key) + it.value }.toSet()
}

// Same as CUSP, but we have pseudonodes as initial and final that routes to set of initial and final switches, respectively.
data class CUSPT(
    val ingressSwitch: Switch,
    val egressSwitch: Switch,
    val initialRouting: Map<Switch, Set<Switch>>,
    val finalRouting: Map<Switch, Set<Switch>>,
    val policy: DFA<Switch>,
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

fun generateCUSPFromUSM(usm: UpdateSynthesisModel, dfa: DFA<Switch>) =
    CUSP(
        setOf(usm.reachability.initialNode),
        setOf(usm.reachability.finalNode),
        usm.switches.associateWith { s -> setOf((usm.initialRouting.find { it.source == s } ?: return@associateWith setOf<Switch>()).target) },
        usm.switches.associateWith { s -> setOf((usm.finalRouting.find { it.source == s } ?: return@associateWith setOf<Switch>()).target) },
        dfa
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

        val dfa: DFA<Switch>
        var time: Long = measureTimeMillis {
            dfa = generateDFAFromUSMProperties(usm)
        }
        if (Options.drawGraphs) dfa.toGraphviz().toFile(File("${GRAPHICS_OUT}/dfa.svg"))

        val cuspt = generateCUSPTFromCUSP(generateCUSPFromUSM(usm, dfa))

        if (Options.drawGraphs) outputPrettyNetwork(usm).toFile(File("${GRAPHICS_OUT}/network.svg"))

        println("Problem file: ${Options.testCase}")
        println("DFA generation time: ${time / 1000.0} seconds \nDFA states: ${dfa.states.size} \nDFA transitions: ${dfa.delta.entries.sumOf { it.value.size }}")

        val subcuspts: List<CUSPT>
        time = measureTimeMillis {
            subcuspts = topologicalDecomposition(cuspt)
        }
        println("Decomposed topology into ${subcuspts.size} subproblems")
        println("Topological decomposition took ${time / 1000.0} seconds")

        var totalMinimum = 0

        for ((i, subcuspt) in subcuspts.withIndex()) {
            println("-- Solving subproblem $i --")

            val modelPath = kotlin.io.path.createTempFile("pnml_model$i")

            val petriGame: PetriGame
            val queryPath: Path
            val updateSwitchCount: Int
            time = measureTimeMillis {
                val (_petriGame, _queryPath, _updateSwitchCount) = generatePetriGameFromCUSPT(subcuspt)
                petriGame = _petriGame
                queryPath = _queryPath
                updateSwitchCount = _updateSwitchCount
            }
            println("Translation to Petri game took ${time / 1000.0} seconds.")

            if (Options.debugPath != null)
                petriGame.apply { addGraphicCoordinatesToPG(this) }

            val pnml = generatePnmlFileFromPetriGame(petriGame)
            if (Options.debugPath != null) {
                Path.of(Options.debugPath!! + "_model$i.pnml").toFile().writeText(pnml)
                Path.of(Options.debugPath!! + "_query$i.q").toFile().writeText(queryPath.toFile().readText())
            }
            modelPath.writeText(pnml)
            println(
                "Petri game switches: ${usm.switches.size} \nPetri game updateable switches: ${updateSwitchCount}\nPetri game places: ${petriGame.places.size} \nPetri game transitions: ${petriGame.transitions.size}" +
                        "\nPetri game arcs: ${petriGame.arcs.size}\nPetri game initial markings: ${petriGame.places.sumOf { it.initialTokens }}"
            )

            val verifier: Verifier
            time = measureTimeMillis {
                verifier = Verifier(modelPath)
                val batches = sequentialSearch(verifier, queryPath, updateSwitchCount)
                println("Subproblem $i solvable with minimum $batches batches.")
                totalMinimum = max(totalMinimum, batches)
            }
            println("Subproblem verification time: ${time / 1000.0} seconds")
        }

        if (totalMinimum == Int.MAX_VALUE) {
            println("Problem is unsolvable!")
        } else {
            println("Minimum batches required: $totalMinimum")
        }
    }
    println("Total program runtime: ${time / 1000.0} seconds")
}

fun generateDFA(){
    val jsonText = Options.testCase.readText()
    val usm = updateSynthesisModelFromJsonText(jsonText)
    val combinedWaypointDFA = genCombinedWaypointDFA(usm)
    combinedWaypointDFA.export(Options.onlyDFAGen!!)
    println("Waypoint DFA successfully generated!")
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


    val onlyDFAGen by argParser.option(
        ArgType.String,
        shortName = "onlydfa",
        description = "Only does the DFA translation, nothing more"
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
    if (Options.onlyDFAGen != null) generateDFA()
    else runProblem()
}