

enum class BatchOrder { UNKNOWN, FIRST, LAST}
data class EquivalenceClass(val switches: Set<Switch>, val batchOrder: BatchOrder)

fun discoverEquivalentClasses(cuspt: CUSPT) {



}

fun onlyInInitial(cuspt: CUSPT) =
    EquivalenceClass(
        cuspt.allSwitches.filter { cuspt.initialRouting[it]!!.isNotEmpty() && cuspt.finalRouting[it]!!.isEmpty() }.toSet(),
        BatchOrder.LAST
    )

fun onlyInFinal(cuspt: CUSPT) =
    EquivalenceClass(
        cuspt.allSwitches.filter { cuspt.initialRouting[it]!!.isEmpty() && cuspt.finalRouting[it]!!.isNotEmpty() }.toSet(),
        BatchOrder.FIRST
    )