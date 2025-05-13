package machine.state.pinnedValues

import org.jacodb.api.jvm.JcMethod

// Arguments to results
typealias MockedCallSequence = List<JcPinnedValue>

class JcSpringMockedCalls(
    private var mockedCalls: Map<JcMethod, MockedCallSequence> = emptyMap()
) {
    fun addMock(method: JcMethod, result: JcPinnedValue) {
        val mockedValues = mockedCalls.getOrDefault(method, listOf())
        val updatedMockedValues = mockedValues + listOf(result)
        mockedCalls += method to updatedMockedValues
    }

    fun getMap() = mockedCalls

    fun copy() = JcSpringMockedCalls(mockedCalls)
}
