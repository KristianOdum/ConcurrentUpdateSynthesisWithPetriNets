import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.io.path.exists

internal class SystemTest {
    fun runJSON(file: String): String {
        assert(SystemTest::class.java.getResource(file) != null)
        assert(Common.verifypnPath.exists())
        val cmd = "java -jar ${Common.mainJar.toAbsolutePath()} ${Common.verifypnPath.toAbsolutePath()} ${SystemTest::class.java.getResource(file)!!.path}"
        val p = Runtime.getRuntime().exec(cmd)

        p.waitFor()

        return p.inputStream.readAllBytes().map { Char(it.toInt()) }.joinToString("")
    }

    @Test
    fun jarTest() {
        val cmd = "java -jar ${Common.mainJar.toAbsolutePath()}"
        val p = Runtime.getRuntime().exec(cmd)

        p.waitFor()
        val t = p.inputStream.readAllBytes().map { Char(it.toInt()) }.joinToString("")

        assert(!t.contains("Error: Could not find or load"))
    }

    @Test
    fun simpleTest() {
        val out = runJSON("test.json")

        assert(out.lowercase().contains("minimum 2 batches"))
    }
}