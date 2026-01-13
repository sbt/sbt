name := "license-info-test"
scalaVersion := "3.3.1"

libraryDependencies += "org.scala-lang" % "scala-library" % scalaVersion.value

TaskKey[Unit]("check") := {
  import java.io.File
  import sbt.io.IO
  
  // Check text file
  val textFile = new File("target/licenses.txt")
  require(textFile.exists(), s"Text file ${textFile.getPath} does not exist")
  val textContent = IO.read(textFile)
  require(textContent.nonEmpty, "Text file is empty")
  require(textContent.contains("scala-library") || textContent.contains("No license specified"), 
    "Text file should contain license information")
  
  // Check JSON file
  val jsonFile = new File("target/licenses.json")
  require(jsonFile.exists(), s"JSON file ${jsonFile.getPath} does not exist")
  val jsonContent = IO.read(jsonFile)
  require(jsonContent.nonEmpty, "JSON file is empty")
  require(jsonContent.trim.startsWith("[") && jsonContent.trim.endsWith("]"), 
    "JSON file should be a valid JSON array")
  require(jsonContent.contains("\"license\"") && jsonContent.contains("\"modules\""), 
    "JSON file should contain license and modules fields")
  
  ()
}

