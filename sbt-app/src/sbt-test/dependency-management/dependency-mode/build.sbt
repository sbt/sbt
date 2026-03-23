lazy val checkDirect = taskKey[Unit]("check direct dependency mode")
lazy val checkPlusOne = taskKey[Unit]("check plusOne dependency mode")
lazy val checkTransitive = taskKey[Unit]("check transitive dependency mode")
lazy val checkDirectTest = taskKey[Unit]("check direct mode applies to Test config")

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
    val cp = (Compile / managedClasspath).value.map(_.data.id)
    // Direct deps should be present
    assert(cp.exists(_.contains("cats-core")),
      s"Expected cats-core in direct mode, got: $cp")
    assert(cp.exists(_.contains("guava")),
      s"Expected guava in direct mode, got: $cp")
    // Transitive deps should be absent
    assert(!cp.exists(_.contains("cats-kernel")),
      s"Expected no cats-kernel in direct mode, got: $cp")
    assert(!cp.exists(_.contains("failureaccess")),
      s"Expected no failureaccess in direct mode, got: $cp")
    // scala-library always present
    assert(cp.exists(n => n.contains("scala3-library") || n.contains("scala-library")),
      s"Expected scala library in direct mode, got: $cp")
  },
  checkPlusOne := {
    val cp = (Compile / managedClasspath).value.map(_.data.id)
    // Direct deps should be present
    assert(cp.exists(_.contains("cats-core")),
      s"Expected cats-core in plusOne mode, got: $cp")
    // Immediate transitive of cats-core should be present
    assert(cp.exists(_.contains("cats-kernel")),
      s"Expected cats-kernel in plusOne mode (direct dep of cats-core), got: $cp")
  },
  checkDirectTest := {
    val cp = (Test / managedClasspath).value.map(_.data.id)
    // Direct deps should be present
    assert(cp.exists(_.contains("cats-core")),
      s"Expected cats-core in direct mode Test config, got: $cp")
    // Transitive deps should be absent
    assert(!cp.exists(_.contains("cats-kernel")),
      s"Expected no cats-kernel in direct mode Test config, got: $cp")
  },
)
