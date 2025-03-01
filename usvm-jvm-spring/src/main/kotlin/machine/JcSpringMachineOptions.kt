package machine

enum class SpringAnalysisMode {
    WebMVCTest,
    SpringBootTest,
}

data class JcSpringMachineOptions(
    val springAnalysisMode: SpringAnalysisMode,
)
