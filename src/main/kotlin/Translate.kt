import java.io.File
import kotlinx.serialization.*
import kotlinx.serialization.json.Json


@Serializable
data class UpdateSynthesisModel(
    @SerialName("Initial_routing")  private val _initRouting: List<List<Int>>,
    @SerialName("Final_routing")    private val _finalRouting: List<List<Int>>,
    @SerialName("Properties")       private val _properties: Properties
) {
    init {
        println("init")
    }
    val initialRouting: List<Edge> = _initRouting.map { Edge(it) }
    val finalRouting: List<Edge> = _finalRouting.map { Edge(it) }
    val waypoint: Waypoint = _properties.waypoint
    val loopFreedom: LoopFreedom = _properties.loopFreedom
    val reachability: Reachability = _properties.reachability

    override fun toString(): String {
        return  "initRouting: $initialRouting \n" +
                "finalRouting: $finalRouting \n" +
                "waypoint: ${waypoint.startNode} <-> ${waypoint.finalNode} :~ ${waypoint.waypoint} \n" +
                "loopfreedom: ${loopFreedom.startNode} \n" +
                "reachability: ${reachability.startNode} -> ${reachability.finalNode} \n"
    }
}

@Serializable
data class Edge (val l: List<Int>) {
    val source: Int = l[0]
    val target: Int = l[1]

    override fun toString(): String {
        return "[$source, $target]"
    }
}

@Serializable
open class Properties (
    @SerialName("Waypoint")     val waypoint: Waypoint,
    @SerialName("LoopFreedom")  val loopFreedom: LoopFreedom,
    @SerialName("Reachability") val reachability: Reachability
    )

@Serializable
class Waypoint(
    @SerialName("startNode")    val startNode: Int,
    @SerialName("finalNode")    val finalNode: Int,
    @SerialName("waypoint")     val waypoint: Int)

@Serializable
class LoopFreedom(
    @SerialName("startNode")    val startNode : Int)

@Serializable
class Reachability(
    @SerialName("startNode")    val startNode : Int,
    @SerialName("finalNode")    val finalNode : Int)

fun generatePetriNetModelFromUpdateNetworkJson(file: File) {
    val data = Json.decodeFromString<UpdateSynthesisModel>(file.readText())
}

