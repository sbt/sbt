# Manual Test Example for JUnitXmlTestsListener Logging

## Problem
Previously, the `writeSuite()` method in `JUnitXmlTestsListener` had a commented-out `System.err.println` statement with a TODO to use proper logging. This meant that when JUnit XML test reports were written, no log message was produced.

## Solution
Replaced the commented code with proper `logger.info()` call that logs when a JUnit XML test report is written.

## Manual Reproduction Steps

### 1. Create a simple test project

Create a directory `test-project` with the following structure:

```
test-project/
  build.sbt
  src/
    test/
      scala/
        ExampleTest.scala
```

**build.sbt:**
```scala
ThisBuild / scalaVersion := "2.13.12"

lazy val root = (project in file("."))
  .settings(
    name := "test-project",
    libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.15" % Test
  )
```

**src/test/scala/ExampleTest.scala:**
```scala
import org.scalatest.funsuite.AnyFunSuite

class ExampleTest extends AnyFunSuite {
  test("example test") {
    assert(1 + 1 == 2)
  }
}
```

### 2. Run tests with JUnit XML output enabled

```bash
cd test-project
sbt test
```

### 3. Verify the log output

With the fix, when tests run, you should see an info-level log message like:

```
[info] Writing JUnit XML test report: /path/to/test-project/target/test-reports/TEST-ExampleTest.xml
```

### 4. Verify the XML file is created

Check that the XML file exists:
```bash
ls -la target/test-reports/TEST-*.xml
```

The file should contain the JUnit XML test report.

## Expected Behavior

- **Before fix**: No log message when test reports are written (commented code)
- **After fix**: Info-level log message appears when test reports are written

## Testing with null logger

The code also handles the case where logger is null (for backward compatibility with older sbt versions). In this case, no log message is produced, but the XML file is still created successfully.

