val proj2 = project

LocalRootProject / name := "proj1"

val check = taskKey[Unit]("Ensure each project is named appropriately")

LocalRootProject / check := {
  assert((LocalRootProject / name).value == "proj1", s"${(LocalRootProject / name).value} == \"proj1\"")
  assert((proj2 / name).value == "boo", s"${(proj2 / name).value} == \"boo\"")
}
