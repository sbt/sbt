scalaSource in Configurations.Compile := (sourceDirectory.value / " scala test ")
 javaSource in Configurations.Compile := (sourceDirectory.value / " java test ")

TaskKey[Unit]("init") := {
  val ss = (scalaSource in Configurations.Compile).value
  val js = ( javaSource in Configurations.Compile).value
  import IO._
  createDirectories(ss :: js :: Nil)
  copyFile(file("changes") / "Test.scala", ss / " Test s.scala")
  copyFile(file("changes") / "A.java", js / "a" / "A.java")
  delete(file("changes"))
}
