import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import translate.*
import verification.Verifier
import verification.sequentialSearch
import java.io.File
import java.lang.Integer.max
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

enum class Verbosity { None, Minimal, Low, High }
typealias v = Verbosity

fun pseudoprint(s: String) = println(s)
fun Verbosity.println(s: String) {
    if (Options.verbosity >= this)
        pseudoprint(s)
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
                + cusp.egressSwitches.associateWith { setOf(-2) }
                + mapOf(-2 to setOf()),
        cusp.finalRouting
                + mapOf(-1 to cusp.ingressSwitches)
                + cusp.egressSwitches.associateWith { setOf(-2) }
                + mapOf(-2 to setOf()),
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
        v.Low.println("DFA generation time: ${time / 1000.0} seconds \nDFA states: ${dfa.states.size} \nDFA transitions: ${dfa.delta.entries.sumOf { it.value.size }}")

        val cuspt = generateCUSPTFromCUSP(generateCUSPFromUSM(usm, dfa))
        v.Minimal.println("Problem file: ${Options.testCase}\n" +
            "Switches to update: ${cuspt.allSwitches.count { cuspt.initialRouting[it] != cuspt.finalRouting[it] }}\n" +
            "Nontrivial switches to update: ${cuspt.allSwitches.count { cuspt.initialRouting[it] != cuspt.finalRouting[it]
                && cuspt.initialRouting[it]!!.isNotEmpty() && cuspt.finalRouting[it]!!.isNotEmpty() }}"
        )

        if (Options.drawGraphs) outputPrettyNetwork(usm).toFile(File("${GRAPHICS_OUT}/network.svg"))

        val subcuspts: List<CUSPT>
        time = measureTimeMillis {
            subcuspts = if (Options.noTopologicalDecompositioning) listOf(cuspt) else topologicalDecomposition(cuspt)
        }
        v.Low.println("Decomposed topology into ${subcuspts.size} subproblems")
        v.Low.println("Topological decomposition took ${time / 1000.0} seconds")

        var totalMinimum = 0

        subproblems@for ((i, subcuspt) in subcuspts.withIndex()) {
            v.High.println("-- Solving subproblem $i --")

            val eqclasses = if (Options.noEquivalenceClasses) setOf() else discoverEquivalenceClasses(subcuspt)

            v.High.println(eqclasses.joinToString("\n"))

            val modelPath = kotlin.io.path.createTempFile("pnml_model$i")

            val petriGame: PetriGame
            val queryPath: Path
            val updateSwitchCount: Int
            time = measureTimeMillis {
                val (_petriGame, _queryPath, _updateSwitchCount) = generatePetriGameFromCUSPT(subcuspt, eqclasses)
                petriGame = _petriGame
                queryPath = _queryPath
                updateSwitchCount = _updateSwitchCount
            }
            v.High.println("Translation to Petri game took ${time / 1000.0} seconds.")

            if (Options.debugPath != null)
                petriGame.apply { addGraphicCoordinatesToPG(this) }

            val pnml = generatePnmlFileFromPetriGame(petriGame)
            if (Options.debugPath != null) {
                Path.of(Options.debugPath!! + "_model$i.pnml").toFile().writeText(pnml)
                Path.of(Options.debugPath!! + "_query$i.q").toFile().writeText(queryPath.toFile().readText())
            }
            modelPath.writeText(pnml)
            v.High.println(
                "Petri game switches: ${usm.switches.size} \nPetri game updateable switches: ${updateSwitchCount}\nPetri game places: ${petriGame.places.size} \nPetri game transitions: ${petriGame.transitions.size}" +
                        "\nPetri game arcs: ${petriGame.arcs.size}\nPetri game initial markings: ${petriGame.places.sumOf { it.initialTokens }}"
            )

            val verifier: Verifier
            time = measureTimeMillis {
                verifier = Verifier(modelPath)
                val batches = sequentialSearch(verifier, queryPath, updateSwitchCount)
                if (batches == Int.MAX_VALUE) {
                    v.Low.println("Subproblem $i unsolvable!")
                } else {
                    v.High.println("Subproblem $i solvable with minimum $batches batches.")
                }
                totalMinimum = max(totalMinimum, batches)
            }
            v.High.println("Subproblem verification time: ${time / 1000.0} seconds")
        }

        if (totalMinimum == Int.MAX_VALUE) {
            v.Minimal.println("Problem is unsolvable!")
        } else {
            v.Minimal.println("Minimum batches required: $totalMinimum")
        }
    }
    v.Minimal.println("Total program runtime: ${time / 1000.0} seconds")
}

fun calcFlipSubpaths(){
    val jsonText = Options.testCase.readText()
    val usm = updateSynthesisModelFromJsonText(jsonText)
    val combinedWaypointDFA = genCombinedWaypointDFA(usm)

    var flipSubpaths = mutableListOf<MutableList<Switch>>()
    if(usm.waypoint.waypoints.count() == 1)
        flipSubpaths.add(mutableListOf(usm.waypoint.waypoints[0]))
    else
        flipSubpaths = combinedWaypointDFA.getWaypointSubPaths(generateCUSPFromUSM(usm, generateDFAFromUSMProperties(usm)))

    File(Options.onlyFLIPSubpaths!!).writeText(flipSubpaths.joinToString(";") { it.joinToString(",") })

    println("Flip subpaths successfully generated!")
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

    val verbosity by argParser.option(
        ArgType.Choice<Verbosity>(),
        shortName = "V",
        description = "Verbosity of print output"
    ).default(Verbosity.Low)


    val onlyFLIPSubpaths by argParser.option(
        ArgType.String,
        shortName = "f",
        description = "Only calculate subpaths for FLIP, nothing more"
    )

    val debugPath by argParser.option(
        ArgType.String,
        shortName = "d",
        fullName = "debugPrefix",
        description = "Output debugging files with the given prefix"
    )

    val noTopologicalDecompositioning by argParser.option(
        ArgType.Boolean,
        shortName = "T",
        description = "Disable topological decompositioning"
    ).default(false)

    val noEquivalenceClasses by argParser.option(
        ArgType.Boolean,
        shortName = "E",
        description = "Disable equivalence classes"
    ).default(false)

    val maxSwicthesInBatch by argParser.option(
        ArgType.Int,
        shortName = "m",
        fullName = "switches_in_batch",
        description = "The maximum number of switches that can be in a batch. 0 = No limit"
    ).default(0)

    val outputVerifyPN by argParser.option(ArgType.Boolean, shortName = "P", description = "output the output from verifypn").default(false)
}

const val version = "1.4"

fun main(args: Array<String>) {
    println("Version: $version \n ${args.joinToString(" ")}")

    Options.argParser.parse(args)
    if (Options.onlyFLIPSubpaths != null) calcFlipSubpaths()
    else runProblem()
}

typealias Batch = Set<Int>
typealias ConupSeq = List<Batch>
fun <T> Collection<T>.powerset(): Set<Set<T>> = when {
    isEmpty() -> setOf(setOf())
    else -> drop(1).powerset().let { it + it.map { it + first() } }
}

fun <T> Collection<T>.powersetne(): Set<Set<T>> =
    powerset().filter { it.isNotEmpty() }.toSet()
fun sols(s: Set<Int>): Set<ConupSeq> =
    if (s.isEmpty()) setOf()
    else s.powersetne().flatMap {
            ot: Batch ->
        if ((s - ot).isNotEmpty())
            sols(s - ot).map { listOf(ot) + it }.toSet()
        else
            setOf(listOf(ot))
    }.toSet()

fun test() {
    val ls = 4
    val switches = (1..ls).toSet()

    println(switches.powersetne())

    println(sols(switches).filter { it.first().contains(1) && it.last().contains(ls) })
    println(sols(switches).filter { it.first().contains(1) && it.last().contains(ls) }.size)
}