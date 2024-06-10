Configurations.Compile / scalaSource := (sourceDirectory.value / " scala test ")
Configurations.Compile / javaSource := (sourceDirectory.value / " java test ")

TaskKey[Unit]("init0") := {
  val ss = (Configurations.Compile / scalaSource).value
  val js = (Configurations.Compile / javaSource).value
  import IO._
  createDirectories(ss :: js :: Nil)
  copyFile(file("changes") / "Test.scala", ss / " Test s.scala")
  copyFile(file("changes") / "A.java", js / "a" / "A.java")
  delete(file("changes"))
}
