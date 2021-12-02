import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import translate.UpdateSynthesisModel
import translate.updateSynthesisModelFromJsonText
import java.io.File
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.io.path.pathString
import kotlin.random.Random

fun addRandomWaypointsToNetworks(numMoreWaypoints: Int, pathToFolder: Path, randomSeed: Int) {
    val random = Random(randomSeed)
    val dir = pathToFolder.toFile()
    assert(dir.isDirectory)
    val newDir = Path.of(pathToFolder.pathString + "_plus${numMoreWaypoints}").toFile()
    if (!newDir.exists())
        newDir.mkdir()

    for (file in dir.walk().iterator()) {
        if (file.isDirectory)
            continue
        val usm = updateSynthesisModelFromJsonText(file.readText())
        val candidateSwitches = usm.switches
        candidateSwitches.toMutableList().removeAll(usm.waypoint.waypoints)

        val newUsm = usm.addWaypoint(candidateSwitches.sorted()[random.nextInt(usm.switches.size)])

        val jElem = Json.encodeToJsonElement(newUsm)

        val newPath = newDir.absolutePath + File.separator + file.name
        val newFile = File(newPath)
        if (!newFile.exists())
            newFile.createNewFile()

        newFile.writeText(jElem.toString())

        println("Added $numMoreWaypoints to ${file.name}")
    }
}

fun UpdateSynthesisModel.addWaypoint(s: Int): UpdateSynthesisModel {
    val initRouting = this.initialRouting.map { listOf(it.source, it.target) }.toSet()
    val finalRouting = this.finalRouting.map { listOf(it.source, it.target) }.toSet()
    val newWaypoint = UpdateSynthesisModel.Waypoint(this.reachability.initialNode, this.reachability.finalNode, this.waypoint.waypoints + listOf(s))
    val properties = UpdateSynthesisModel.Properties(newWaypoint, this.loopFreedom, this.reachability)

    return UpdateSynthesisModel(initRouting, finalRouting, properties)
}