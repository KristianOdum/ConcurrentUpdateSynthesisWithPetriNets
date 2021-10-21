import translate.*
import verification.Verifier
import verification.bisectionSearch
import java.io.File

fun main() {
    val jsonText = File(PetriGame::class.java.getResource("test.json")!!.toURI()).readText()
    val usm = updateSynthesisModelFromJsonText(jsonText)
    val nfa = generateNFAFromUSM(usm)

    val pg = generatePetriGameModelFromUpdateSynthesisNetwork(usm)
    addGraphicCoordinatesToPG(pg)
    generatePnmlFileFromPetriGame(pg, "output.pnml")

//    val verifier = Verifier("verifypn-linux64", "ConcurrentRoutingUpdate.pnml")
//    bisectionSearch(verifier, "tempQuery.q", 50)

}


