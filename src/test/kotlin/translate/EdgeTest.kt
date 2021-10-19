package translate

import org.junit.jupiter.api.Test
import kotlin.math.E

internal class EdgeTest {
    @Test
    fun edgeAreEqual() {
        val edge1 = Edge(1,2)
        val edge2 = Edge(1,2)

        assert(edge1 == edge2)
    }

    @Test
    fun edgeAreNotEqual() {
        val edge1 = Edge(1,2)
        val edge2 = Edge(6,3)

        assert(edge1 != edge2)
    }
}