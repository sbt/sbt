import sbt.input.aggregation.Build

val root = Build.root
val foo = Build.foo
val bar = Build.bar

Global / watchTriggers += baseDirectory.value.toGlob / "baz.txt"
