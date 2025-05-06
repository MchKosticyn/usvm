package machine.state.pinnedValues

import org.jacodb.api.jvm.JcMethod
import org.usvm.UExpr
import org.usvm.USort

// Arguments to results
typealias MockedCallSequence = List<JcPinnedValue>

class JcSpringMockedCalls(
    private var mockedCalls: Map<JcMethod, MockedCallSequence> = emptyMap()
) {
    fun addMock(method: JcMethod, result: JcPinnedValue) {
        val list = mockedCalls.getOrDefault(method, listOf())
        mockedCalls = mockedCalls.filter { it.key != method }
        mockedCalls += method to list + listOf(result)
    }

    fun getMap() = mockedCalls

    fun copy() = JcSpringMockedCalls(mockedCalls)
}
