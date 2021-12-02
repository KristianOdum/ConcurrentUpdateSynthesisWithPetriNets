import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import translate.UpdateSynthesisModel
import translate.updateSynthesisModelFromJsonText
import java.awt.font.FontRenderContext
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

    files@for (file in dir.walk().iterator()) {
        if (file.isDirectory)
            continue
        val usm = updateSynthesisModelFromJsonText(file.readText())

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

fun UpdateSynthesisModel.addWaypoints(waypoints: List<Int>): UpdateSynthesisModel {
    val initRouting = this.initialRouting.map { listOf(it.source, it.target) }.toSet()
    val finalRouting = this.finalRouting.map { listOf(it.source, it.target) }.toSet()
    val newWaypoint = UpdateSynthesisModel.Waypoint(this.reachability.initialNode, this.reachability.finalNode, this.waypoint.waypoints + waypoints)
    val properties = UpdateSynthesisModel.Properties(newWaypoint, this.loopFreedom, this.reachability)

    return UpdateSynthesisModel(initRouting, finalRouting, properties)
}