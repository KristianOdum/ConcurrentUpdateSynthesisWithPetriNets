import translate.generatePetriGameModelFromUpdateNetworkJson
import translate.generatePnmlFileFromPetriGame
import java.io.File

fun main() {
    val f = File("/home/slorup/git/ConcurrentUpdateSynthesisWithPetriNets/artefact/data/test_json/test.json")
    val regex = """waypoint": (\d+)""".toRegex()
    val text = regex.replace(f.readText()){
        m -> "waypoint\": [" + m.groups[1]!!.value + "]"
    }
    val pg = generatePetriGameModelFromUpdateNetworkJson(text)

    generatePnmlFileFromPetriGame(pg, "/home/slorup/git/ConcurrentUpdateSynthesisWithPetriNets/output.pnml")
}
