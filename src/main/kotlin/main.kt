import translate.generatePetriGameModelFromUpdateNetworkJson
import translate.generatePnmlFileFromPetriGame
import verification.Verifier
import verification.bisectionSearch
import java.io.File

fun main() {
    val jsonText = File(PetriGame::class.java.getResource("test.json")!!.toURI()).readText()
    val pg = generatePetriGameModelFromUpdateNetworkJson(jsonText)
    PetriPrettyPlotter().addGraphicsCoordinatesToPG(pg)
    generatePnmlFileFromPetriGame(pg, "output.pnml")

    //val verifier = Verifier("verifypn-linux64", "ConcurrentRoutingUpdate.pnml")
    //print(bisectionSearch(verifier, "ConcurrentRoutingUpdate.q", 2, 50))

}


