class PetriGame(
    val places: List<Place>,
    val transitions: List<Transition>,
    val arcs: List<Arc>
    )

var nextId = 0
    get() = field++

data class Place(val initialMarkings: Int, val name: String) {
    val id: Int = nextId

    override fun equals(other: Any?): Boolean {
        return other is Place
                && other.name == this.name
    }

    override fun hashCode(): Int {
        var result = initialMarkings
        result = 31 * result + name.hashCode()
        return result
    }
}

data class Transition(val controllable: Boolean, val name: String) {
    val id: Int = nextId

    override fun equals(other: Any?): Boolean {
        return other is Transition
                && other.name == this.name
                && other.controllable == this.controllable
    }

    override fun hashCode(): Int {
        var result = controllable.hashCode()
        result = 31 * result + name.hashCode()
        return result
    }
}

data class Arc(val sourceName: String, val targetName: String, val weight: Int = 1, val inhibitor: Boolean) {
    val name: String = nextId.toString()

    override fun equals(other: Any?): Boolean {
        return other is Arc
                && other.sourceName == this.sourceName
                && other.targetName == this.targetName
                && other.inhibitor == this.inhibitor
    }

    override fun hashCode(): Int {
        var result = sourceName.hashCode()
        result = 31 * result + targetName.hashCode()
        result = 31 * result + inhibitor.hashCode()
        return result
    }
}
