# Automatic mock configuration
`usvm-jvm-mocks` is a module for the [USVM](https://github.com/MchKosticyn/usvm) symbolic virtual machine.  
It automates the configuration of mocked methods in tests, allowing mock values to be generated automatically instead of manually.

## Features ✨
- Detects and tracks mocked objects in tests
- Generates mock values for mocks' methods automatically using symbolic execution
- Produces ready-to-use mock configurations for test methods and even integrates fully into the original test (don't worry, it's a copy in case you don't like the result)
- Integrates with the existing USVM infrastructure (`usvm-jvm`)

## Requirements

- JDK 17+
- Kotlin, Java
- Gradle

## Getting started
Clone the repo:
```bash
git clone git@github.com:sofyak0zyreva/usvm.git
```
Switch to dev's branch:
```bash
git switch mocks2
```
Cause *second time's the charm*✨ You're all set!

## Usage
1. Place sample `.java` files --- your tests ---  [here](./src/samples/java/org/usvm/samples/)
2. Create corresponding test file (yes, test file for your test file) [here](./src/test/kotlin/org/usvm/samples/):
### Example
```kotlin
package org.usvm.samples

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults

class ExampleTest : MocksTestRunner() {
  @BeforeEach
  fun reset() {
      cleanUp()
  }

  @Test
  fun test() {
      checkDiscoveredPropertiesWithExceptions(
          ExampleClass::exampleMethodUnderTest,
          ignoreNumberOfAnalysisResults,
          { _, _, r -> r.getOrNull() == null }
      )
  }
}
```
`_` must be one greater than the number of arguments in the function under test (just trust me on this one).

3. Run tests:
```bash
./gradlew :usvm-jvm-mocks:test
```
or just a specific test if you so prefer. That's it! The result is waiting for you in the [testOutput](./src/samples/java/org/usvm/samples/testOutput) directory. Enjoy! There will be improvements so stay in touch✨

## Contacts
[sofyak0zyreva](https://github.com/sofyak0zyreva) (tg @soffque) in case you have questions

## License
The product is distributed under MIT license. See [`LICENSE`](LICENSE) for details.

