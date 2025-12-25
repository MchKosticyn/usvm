package machine.instructions

import org.usvm.test.api.UTestInst

/**
 * This class simply represents instructions that render should transform into Java-code.
 */
class UTestMockConfigInfo(val instructions: List<Pair<UTestInst, String>>)
