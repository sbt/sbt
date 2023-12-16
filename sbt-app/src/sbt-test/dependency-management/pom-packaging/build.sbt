val root = project in file(".")
val subJar = project in file("subJar")
def warArtifact = (Compile / packageBin / artifact) ~= (_ withType "war" withExtension "war")
val subWar = project in file("subWar") settings warArtifact
val subParent = project in file("subParent") settings ((Compile / publishArtifact) := false)

val checkPom = taskKey[Unit]("")
(ThisBuild / checkPom) := {
  checkPackaging((subJar / makePom).value, "jar", fileConverter.value)
  checkPackaging((subWar / makePom).value, "war", fileConverter.value)
  checkPackaging((subParent / makePom).value, "pom", fileConverter.value)
}

def checkPackaging(vf: xsbti.HashedVirtualFileRef, expected: String, converter: xsbti.FileConverter) = {
  val packaging = (xml.XML.loadFile(converter.toPath(vf).toFile) \\ "packaging").text
  if (packaging != expected)
    sys error s"Incorrect packaging for '$vf'.  Expected '$expected', but got '$packaging'"
}
