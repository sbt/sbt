import sbt.Def.Initialize

lazy val checkPom = taskKey[Unit]("")

lazy val root = (project in file(".")).
  settings(
    checkPom in ThisBuild := checkPomTask.value
  )

lazy val subJar = (project in file("subJar"))

lazy val subWar = (project in file("subWar")).
  settings(
    warArtifact
  )

lazy val subParent = (project in file("subParent")).
  settings(
    publishArtifact in Compile := false
  )

def art(p: ProjectReference) = makePom in p
def checkPomTask: Initialize[Task[Unit]] =
  (art(subJar), art(subWar), art(subParent)) map { (jar, war, pom) =>
    checkPackaging(jar, "jar")
    checkPackaging(war, "war")
    checkPackaging(pom, "pom")
  }

def checkPackaging(pom: File, expected: String) =
{
  val packaging = (xml.XML.loadFile(pom) \\ "packaging").text
  if(packaging != expected) sys.error("Incorrect packaging for '" + pom + "'.  Expected '" + expected + "', but got '" + packaging + "'")
}
def warArtifact = artifact in (Compile, packageBin) ~= (_ withType "war" withExtension "war")
