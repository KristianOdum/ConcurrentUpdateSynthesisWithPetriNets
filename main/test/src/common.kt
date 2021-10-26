import java.nio.file.Path
import kotlin.io.path.exists

object Common {
    val verifypnPath: Path = Path.of(System.getenv("VERIFYPN_PATH"))
    val mainJar: Path = Path.of("../main.jar")
}
