import java.nio.file.Path
import kotlin.io.path.exists

val verifypnPath = Path.of(System.getenv("VERIFYPN_PATH"))
val mainJar = Path.of("../main.jar")