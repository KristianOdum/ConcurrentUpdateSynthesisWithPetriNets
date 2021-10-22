import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.io.path.exists

internal class SystemTest {

    fun runJSON(file: String): String {
        assert(SystemTest::class.java.getResource(file) != null)
        assert(verifypnPath.exists())
        val cmd = "java -jar ${mainJar.toAbsolutePath()} ${verifypnPath.toAbsolutePath()} ${SystemTest::class.java.getResource(file)!!.path}"
        val p = Runtime.getRuntime().exec(cmd)

        p.waitFor()

        return p.inputStream.readAllBytes().map { Char(it.toInt()) }.joinToString("")
    }

    @Test
    fun simpleTest() {
        val out = runJSON("test.json")

        assert(out.contains("Minimum 2 batches"))
    }

}