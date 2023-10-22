val proj2 = project

name := "proj1"

val check = taskKey[Unit]("Ensure each project is named appropriately")

check := {
  require(name.value == "proj1")
  require((name in proj2).value == "boo")
}
