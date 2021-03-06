import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import translate.updateSynthesisModelFromJsonText
import java.io.File
import java.nio.file.Path
import kotlin.io.path.pathString
import kotlin.random.Random

fun addRandomWaypointsToNetworks(numMoreWaypoints: Int, pathToFolder: Path, randomSeed: Int) {
    val random = Random(randomSeed)
    val dir = pathToFolder.toFile()
    assert(dir.isDirectory)
    val newDir = Path.of(pathToFolder.pathString + "_plus${numMoreWaypoints}").toFile()
    if (!newDir.exists())
        newDir.mkdir()

    files@for (file in dir.walk().iterator()) {
        if (file.isDirectory)
            continue
        val usm: UpdateSynthesisModel = updateSynthesisModelFromJsonText(file.readText())

        val candidateSwitches = usm.initialRouting.filter { i_it -> usm.finalRouting.map { it.source }.contains(i_it.source) }.map { it.source }.toMutableList()
        candidateSwitches -= usm.waypoint.waypoints

        val newWaypoints = mutableListOf<Int>()
        for (i in 1..numMoreWaypoints) {
            if (candidateSwitches.isEmpty()) {
                println("Could not add $numMoreWaypoints to ${file.name}")
                continue@files
            }
            val new = candidateSwitches.sorted()[random.nextInt(candidateSwitches.size)]
            newWaypoints += new
            candidateSwitches -= new
        }
        val newUsm = usm.addWaypoints(newWaypoints)
        assert(newUsm.waypoint.waypoints.size == newUsm.waypoint.waypoints.distinct().size)
        assert(newUsm.waypoint.waypoints.size == usm.waypoint.waypoints.size + numMoreWaypoints)

        val jElem = Json.encodeToJsonElement(newUsm)

        val newPath = newDir.absolutePath + File.separator + file.name
        val newFile = File(newPath)
        if (!newFile.exists())
            newFile.createNewFile()
        newFile.writeText(jElem.toString())

        println("Added $numMoreWaypoints to ${file.name}")
    }
}

fun addConditionalEnforcementToNetworks(pathToFolder: Path, randomSeed: Int) {
    val random = Random(randomSeed)
    val dir = pathToFolder.toFile()
    assert(dir.isDirectory)
    val newDir = Path.of(pathToFolder.pathString + "_cond_enf").toFile()
    if (!newDir.exists())
        newDir.mkdir()

    files@for (file in dir.walk().iterator()) {
        if (file.isDirectory)
            continue
        val usm: UpdateSynthesisModel = updateSynthesisModelFromJsonText(file.readText())

        val candidateSwitches = usm.switches.toMutableList()

        val s = candidateSwitches[random.nextInt(candidateSwitches.size)]
        candidateSwitches -= s

        if (s in usm.initialRouting.map { it.source })
            candidateSwitches.filter { it in usm.initialRouting.map { it.source } }
        else if (s in usm.finalRouting.map { it.source })
            candidateSwitches.filter { it in usm.finalRouting.map {it.source} }
        val sPrime = candidateSwitches[random.nextInt(candidateSwitches.size)]

        val newUsm: UpdateSynthesisModel = usm.addConditionalEnforcement(s, sPrime)

        val jElem = Json.encodeToJsonElement(newUsm)

        val newPath = newDir.absolutePath + File.separator + file.name
        val newFile = File(newPath)
        if (!newFile.exists())
            newFile.createNewFile()
        newFile.writeText(jElem.toString())

        println("Added conditional enforcement to ${file.name}")
    }
}

fun UpdateSynthesisModel.addWaypoints(waypoints: List<Int>): UpdateSynthesisModel {
    val initRouting = this.initialRouting.map { listOf(it.source, it.target) }.toSet()
    val finalRouting = this.finalRouting.map { listOf(it.source, it.target) }.toSet()
    val newWaypoint = UpdateSynthesisModel.Waypoint(this.reachability.initialNode, this.reachability.finalNode, this.waypoint.waypoints + waypoints)
    val properties = UpdateSynthesisModel.Properties(newWaypoint, this.conditionalEnforcement, this.loopFreedom, this.reachability)

    return UpdateSynthesisModel(initRouting, finalRouting, properties)
}

fun UpdateSynthesisModel.addConditionalEnforcement(s: Int, sPrime: Int): UpdateSynthesisModel {
    val initRouting = this.initialRouting.map { listOf(it.source, it.target) }.toSet()
    val finalRouting = this.finalRouting.map { listOf(it.source, it.target) }.toSet()
    val condEnf = UpdateSynthesisModel.ConditionalEnforcement(s, sPrime)
    val properties = UpdateSynthesisModel.Properties(waypoint, condEnf, loopFreedom, reachability)

    return UpdateSynthesisModel(initRouting, finalRouting, properties)
}
