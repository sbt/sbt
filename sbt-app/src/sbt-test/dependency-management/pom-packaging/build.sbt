val root = project in file(".")

val subJar = project in file("subJar")

def warArtifact = (Compile / packageBin / artifact) ~= (_ withType "war" withExtension "war")
val subWar = project in file("subWar") settings warArtifact

val subParent = project in file("subParent") settings ((Compile / publishArtifact) := false)

val checkPom = taskKey[Unit]("")
(ThisBuild / checkPom) := {
  checkPackaging((subJar / makePom).value, "jar")
  checkPackaging((subWar / makePom).value, "war")
  checkPackaging((subParent / makePom).value, "pom")
}

def checkPackaging(pom: File, expected: String) = {
  val packaging = (xml.XML.loadFile(pom) \\ "packaging").text
  if (packaging != expected)
    sys error s"Incorrect packaging for '$pom'.  Expected '$expected', but got '$packaging'"
}
