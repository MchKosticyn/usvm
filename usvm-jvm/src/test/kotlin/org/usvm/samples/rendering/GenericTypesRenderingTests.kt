package org.usvm.samples.rendering

import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults

class GenericTypesRenderingTests: JavaMethodTestRunner() {
    @Test
    fun unboundedWildcardInUsageTest() {
        checkDiscoveredProperties(
            GenericTypes::unboundedWildcardInUsage,
            ignoreNumberOfAnalysisResults
        )
    }

    @Test
    fun noGenericArgumentTest() {
        checkDiscoveredProperties(
            GenericTypes::noGenericArgument,
            ignoreNumberOfAnalysisResults
        )
    }
    @Test
    fun wildcardsMiscTest() {
        checkDiscoveredProperties(
            GenericTypes::wildcardsMisc,
            ignoreNumberOfAnalysisResults
        )
    }
    @Test
    fun noGenericArgumentInnerTest() {
        checkDiscoveredProperties(
            GenericTypes::noGenericArgumentInner,
            ignoreNumberOfAnalysisResults
        )
    }

    @Test
    fun methodWithGenericParameterAndReturnTypeTest() {
        checkDiscoveredPropertiesWithExceptions<Int, Int>(
            GenericTypes::methodWithGenericParameterAndReturnType,
            ignoreNumberOfAnalysisResults
        )
    }

    @Test
    fun notParametrizedCollectionTest() {
        checkDiscoveredPropertiesWithExceptions(
            GenericTypes::notParametrizedCollection,
            ignoreNumberOfAnalysisResults
        )
    }
}