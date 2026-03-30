lazy val `a + b` = "2.13.18"

// https://github.com/scala/scala3/blob/3.8.3/library/src/scala/reflect/NameTransformer.scala#L47-L64
lazy val ~=<>!#%^&|*/+-:\\?@ = "my-name"

scalaVersion := `a + b`

name := ~=<>!#%^&|*/+-:\\?@

InputKey[Unit]("check") := {
  assert(scalaVersion.value == "2.13.18")
  assert(name.value == "my-name")
}
