lazy val `import` = (project in file("."))
  .settings(
    name := "Hello"
  )

val `return` = inputKey[Unit]("Check that commands can be defined as back-ticked variables")


`return` := {
	()
}


val `ї` = inputKey[Unit]("Check that we can defined unicode commands")

`ї` := {
	()
}

