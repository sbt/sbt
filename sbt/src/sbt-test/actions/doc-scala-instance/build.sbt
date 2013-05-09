lazy val a = project.settings(
	scalaVersion := "2.9.2",
	scalaInstance in (Compile,doc) := (scalaInstance in b).value,
	// 2.10.1-only, so this will only succeed if `doc` recognizes the more specific scalaInstance scoped to `doc`
	scalacOptions in (Compile,doc) += "-implicits"
)

lazy val b = project.settings(
	scalaVersion := "2.10.1"
)
