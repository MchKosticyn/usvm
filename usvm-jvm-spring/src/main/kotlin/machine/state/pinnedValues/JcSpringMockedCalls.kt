package machine.state.pinnedValues

import org.jacodb.api.jvm.JcMethod
import org.usvm.UExpr
import org.usvm.USort

// Arguments to results
typealias MockedCallSequence = MutableList<JcPinnedValue>

class JcSpringMockedCalls(
    private var mockedCalls: Map<JcMethod, MockedCallSequence> = emptyMap()
) {
    fun addMock(method: JcMethod, result: JcPinnedValue) {
        if (mockedCalls[method] == null) {
            mockedCalls += method to arrayListOf()
        }
        mockedCalls[method]!!.add(result)
    }

    fun getMap() = mockedCalls

    fun copy() = JcSpringMockedCalls(mockedCalls)
}
