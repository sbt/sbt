lazy val checkDirect = taskKey[Unit]("check direct dependency mode")
lazy val checkPlusOne = taskKey[Unit]("check plusOne dependency mode")
lazy val checkTransitive = taskKey[Unit]("check transitive dependency mode")
lazy val checkDirectTest = taskKey[Unit]("check direct mode in Test config: runtime classpath is unfiltered")

lazy val root = (project in file(".")).settings(
  scalaVersion := "3.7.4",
  // cats-core has transitive dep on cats-kernel
  libraryDependencies += "org.typelevel" %% "cats-core" % "2.12.0",
  // A Java library with transitive deps (guava pulls in failureaccess, etc.)
  libraryDependencies += "com.google.guava" % "guava" % "33.4.0-jre",
  checkTransitive := {
    val cp = (Compile / managedClasspath).value.map(_.data.id)
    assert(cp.exists(_.contains("cats-core")),
      s"Expected cats-core in transitive mode, got: $cp")
    assert(cp.exists(_.contains("cats-kernel")),
      s"Expected cats-kernel in transitive mode, got: $cp")
    assert(cp.exists(_.contains("guava")),
      s"Expected guava in transitive mode, got: $cp")
  },
  checkDirect := {
    // managedClasspath is always unfiltered (transitive)
    val cp = (Compile / managedClasspath).value.map(_.data.id)
    assert(cp.exists(_.contains("cats-core")),
      s"Expected cats-core in managedClasspath, got: $cp")
    assert(cp.exists(_.contains("cats-kernel")),
      s"Expected cats-kernel in managedClasspath (always transitive), got: $cp")
    // filteredDependencyClasspath respects dependencyMode
    val filtered = (Compile / filteredDependencyClasspath).value.map(_.data.id)
    assert(filtered.exists(_.contains("cats-core")),
      s"Expected cats-core in filtered classpath, got: $filtered")
    assert(filtered.exists(_.contains("guava")),
      s"Expected guava in filtered classpath, got: $filtered")
    assert(!filtered.exists(_.contains("cats-kernel")),
      s"Expected no cats-kernel in filtered classpath (direct mode), got: $filtered")
    assert(!filtered.exists(_.contains("failureaccess")),
      s"Expected no failureaccess in filtered classpath (direct mode), got: $filtered")
    assert(filtered.exists(n => n.contains("scala3-library") || n.contains("scala-library")),
      s"Expected scala library in filtered classpath, got: $filtered")
  },
  checkPlusOne := {
    val filtered = (Compile / filteredDependencyClasspath).value.map(_.data.id)
    // Direct deps should be present
    assert(filtered.exists(_.contains("cats-core")),
      s"Expected cats-core in plusOne mode, got: $filtered")
    // Immediate transitive of cats-core should be present
    assert(filtered.exists(_.contains("cats-kernel")),
      s"Expected cats-kernel in plusOne mode (direct dep of cats-core), got: $filtered")
  },
  checkDirectTest := {
    // Test / dependencyClasspath should be unfiltered (includes transitive deps)
    val cp = (Test / dependencyClasspath).value.map(_.data.id)
    assert(cp.exists(_.contains("cats-core")),
      s"Expected cats-core in Test dependencyClasspath, got: $cp")
    assert(cp.exists(_.contains("cats-kernel")),
      s"Expected cats-kernel in Test dependencyClasspath (transitive deps preserved), got: $cp")
    // Test / filteredDependencyClasspath should be filtered
    val filtered = (Test / filteredDependencyClasspath).value.map(_.data.id)
    assert(filtered.exists(_.contains("cats-core")),
      s"Expected cats-core in Test filtered classpath, got: $filtered")
    assert(!filtered.exists(_.contains("cats-kernel")),
      s"Expected no cats-kernel in Test filtered classpath (direct mode), got: $filtered")
  },
)
