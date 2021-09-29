import translate.generatePetriGameModelFromUpdateNetworkJson
import translate.generatePnmlFileFromPetriGame
import java.io.File

fun main() {
    val f = File(PetriGame::class.java.getResource("test.json")!!.toURI())
    val text = f.readText()
    val pg = generatePetriGameModelFromUpdateNetworkJson(text)

    generatePnmlFileFromPetriGame(pg, "output.pnml")
}
