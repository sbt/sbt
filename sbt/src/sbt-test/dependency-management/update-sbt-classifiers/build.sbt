ThisBuild / scalaVersion := "2.11.12"

ivyConfiguration := {
  throw new RuntimeException("updateSbtClassifiers should use updateSbtClassifiers / ivyConfiguration")
}

dependencyResolution := {
  throw new RuntimeException("updateSbtClassifiers should use updateSbtClassifiers / dependencyResolution")
}

scalaOrganization := "doesnt.exist"

