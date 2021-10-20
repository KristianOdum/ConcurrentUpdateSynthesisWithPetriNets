package translate
class NFA {
    val states: MutableSet<Int> = mutableSetOf()
    val actions: MutableSet<Action> =  mutableSetOf()

    fun addAction(a: Action): NFA {
        actions += a

        if (!states.contains(a.from))
            states += a.from
        if (!states.contains(a.to))
            states += a.to

        return this
    }

    fun addAction(from: Int, label: Int, to: Int, epsilon: Boolean = false): NFA {
        addAction(Action(from, label, to, epsilon))
        return this
    }

    fun addState(a: Int): NFA {
        states += a
        return this
    }
}


open class Action(val from: Int, val label: Int, val to: Int, val epsilon: Boolean = false) { }

//class EpsilonAction(val from: Int, val to: Int) : Action(from, to) { }


fun generateNFAFromUSM(usm: UpdateSynthesisModel): NFA {
    val nfa = NFA()

    val initialSwitch = usm.reachability.initialNode
    val finalSwitch = usm.reachability.finalNode
    var nextId = 0
    val initialState = nextId++

    // NFA for reachability
    nfa.addAction(initialState, initialSwitch, nextId++).addAction(nextId, -1, nextId++, true)
    nfa.apply {
        for (s in (usm.switches subtract setOf(finalSwitch)))
            addAction(nextId - 1, s, nextId)
    }

    return nfa
}

//class SwitchNFAState(val value: Int, val final: Boolean = false) : State { }
//
//class EdgeNFAEvent(val switches: Set<Int>) : Event<SwitchNFAState> {
//    override fun accept(from: SwitchNFAState, to: SwitchNFAState) { }
//}
//
//fun NFA.Builder<SwitchNFAState, EdgeNFAEvent>.addTransition(from:Int, act: Int, to:Int): NFA.Builder<SwitchNFAState, EdgeNFAEvent> =
//    this.addTransition(SwitchNFAState(from), EdgeNFAEvent(act), SwitchNFAState(to))
