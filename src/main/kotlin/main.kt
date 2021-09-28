import translate.generatePetriGameModelFromUpdateNetworkJson
import translate.generatePnmlFileFromPetriGame
import java.io.File

fun main() {
    val f = File("/home/odum/Downloads/test.json")
    val text = f.readText()
    val pg = generatePetriGameModelFromUpdateNetworkJson(text)

    generatePnmlFileFromPetriGame(pg, "/home/odum/git/ConcurrentUpdateSynthesisWithPetriNets/output.pnml")
}
