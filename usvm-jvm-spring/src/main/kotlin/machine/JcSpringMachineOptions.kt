package machine

import util.SpringApproximationPaths

data class JcSpringMachineOptions(
    val springTestGenerationMode: JcSpringTestGenerationMode = JcSpringTestGenerationMode.SpringBootTest,
    val springAnalysisMode: JcSpringAnalysisMode = JcSpringAnalysisMode.EdgeCases,
    val springApproximationPaths: SpringApproximationPaths = SpringApproximationPaths()
)
