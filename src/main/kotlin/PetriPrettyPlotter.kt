import guru.nidi.graphviz.model.*
import guru.nidi.graphviz.*
import guru.nidi.graphviz.engine.Format
import guru.nidi.graphviz.model.Factory.node
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.lang.Exception

class PetriPrettyPlotter {
    fun addGraphicsCoordinatesToPG(pg: PetriGame) {
        val nodes: MutableList<Node> = mutableListOf()
        val edges: MutableList<Link> = mutableListOf()
        val nameToNodeMap: MutableMap<String, Node> = mutableMapOf()

        var graph: MutableGraph = graph(directed = true) {  }

        for (p: Place in pg.places) {
            val n = node(p.name)
            nodes.add(n)
            nameToNodeMap[p.name] = n
        }

        for (t: Transition in pg.transitions) {
            val n = node(t.name)
            nodes.add(n)
            nameToNodeMap[t.name] = n
        }

        graph = graph(directed = true) {
            for (a: Arc in pg.arcs) {
                    a.sourceName - a.targetName
            }
        }

        val nameToPos: MutableMap<String, Pair<Double, Double>> = mutableMapOf()

        val gv = graph.toGraphviz()
        val jsonText = gv.render(Format.JSON).toString()
        val jRoot = Json.parseToJsonElement(jsonText).jsonObject["objects"]

        for (e in jRoot!!.jsonArray) {
            val name = e.jsonObject["name"]!!.jsonPrimitive.content
            val pos1: Double = e.jsonObject["_ldraw_"]!!.jsonArray[2].jsonObject["pt"]!!.jsonArray[0].jsonPrimitive.toString().toDouble()
            val pos2: Double = e.jsonObject["_ldraw_"]!!.jsonArray[2].jsonObject["pt"]!!.jsonArray[1].jsonPrimitive.toString().toDouble()
            nameToPos[name] = Pair(pos1, pos2)
        }

        for (nameAndPos in nameToPos) {
            val name = nameAndPos.key
            val p = pg.places.find { it.name == nameAndPos.key }
            val t = pg.transitions.find { it.name == nameAndPos.key }
            val pos = nameAndPos.value.toList().map { it.toInt() }.zipWithNext()
            if (p != null) {
                p.pos = pos.first()
            }
            else if (t != null) {
                t.pos = pos.first()
            }
            else
                throw Exception("Positional graphics could not match name with any place or transition.")
        }
    }
}