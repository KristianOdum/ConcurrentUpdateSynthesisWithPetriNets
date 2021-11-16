import guru.nidi.graphviz.attribute.Color.*
import guru.nidi.graphviz.engine.Format
import guru.nidi.graphviz.graph
import guru.nidi.graphviz.toGraphviz
import translate.UpdateSynthesisModel
import java.awt.image.BufferedImage
import java.io.File

fun outputPrettyNetwork(usm: UpdateSynthesisModel) {

    val g = graph(directed = true) {
        usm.reachability.initialNode.toString().get().attrs().add("shape","hexagon")
        usm.reachability.finalNode.toString().get().attrs().add("shape","doublecircle")

        for ((from, to) in usm.initialRouting) {
            (from.toString() - to.toString()).attrs().add("color","blue")
        }
        for ((from, to) in usm.finalRouting) {
            (from.toString() - to.toString()).attrs().add("color","red")
        }
    }

    g.toGraphviz().render(Format.PNG).toFile(File("$GRAPHICS_OUT/network.png").apply { createNewFile() })
}