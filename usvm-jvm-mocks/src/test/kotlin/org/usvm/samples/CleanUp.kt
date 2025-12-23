package org.usvm.samples

import machine.mockedMethods
import machine.mockedMethodsValues
import machine.mocksMap
import machine.varnamesMap

fun cleanUp() {
    mocksMap.clear()
    mockedMethods.clear()
    mockedMethodsValues.clear()
    varnamesMap.clear()
}
