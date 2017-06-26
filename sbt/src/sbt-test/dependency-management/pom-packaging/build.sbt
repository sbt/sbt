val root = project in file(".")

val subJar = project in file("subJar")

def warArtifact = artifact in (Compile, packageBin) ~= (_ withType "war" withExtension "war")
val subWar = project in file("subWar") settings warArtifact

val subParent = project in file("subParent") settings (publishArtifact in Compile := false)

val checkPom = taskKey[Unit]("")
checkPom in ThisBuild := {
  checkPackaging((makePom in subJar).value, "jar")
  checkPackaging((makePom in subWar).value, "war")
  checkPackaging((makePom in subParent).value, "pom")
}

def checkPackaging(pom: File, expected: String) = {
  val packaging = (xml.XML.loadFile(pom) \\ "packaging").text
  if (packaging != expected)
    sys error s"Incorrect packaging for '$pom'.  Expected '$expected', but got '$packaging'"
}
