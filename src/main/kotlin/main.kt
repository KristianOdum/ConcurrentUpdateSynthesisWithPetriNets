import translate.generatePetriGameModelFromUpdateNetworkJson
import translate.generatePnmlFileFromPetriGame
import java.io.File

fun main() {
    val jsonText = File(PetriGame::class.java.getResource("test.json")!!.toURI()).readText()
    val pg = generatePetriGameModelFromUpdateNetworkJson(jsonText)
    generatePnmlFileFromPetriGame(pg, "output.pnml")
}
