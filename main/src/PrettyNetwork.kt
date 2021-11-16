import guru.nidi.graphviz.engine.Format
import guru.nidi.graphviz.engine.Renderer
import guru.nidi.graphviz.graph
import guru.nidi.graphviz.toGraphviz
import translate.UpdateSynthesisModel
import java.io.File

fun outputPrettyNetwork(usm: UpdateSynthesisModel): Renderer {
    val g = graph(directed = true) {
        usm.reachability.initialNode.toString().get().attrs().add("shape","hexagon")
        usm.reachability.finalNode.toString().get().attrs().add("shape","doublecircle")
        usm.waypoint.waypoints.forEach { it.toString().get().attrs().add("shape", "house") }

        for ((from, to) in usm.initialRouting) {
            (from.toString() - to.toString()).attrs().add("color","blue")
        }
        for ((from, to) in usm.finalRouting) {
            (from.toString() - to.toString()).attrs().add("color","red")
        }
    }

    return g.toGraphviz().render(Format.SVG)
}
