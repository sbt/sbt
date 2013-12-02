organization in ThisBuild := "org.example"

version in ThisBuild := "1.0-SNAPSHOT"


lazy val main = project.settings(
	uniqueName,
	libraryDependencies += (projectID in library).value
)

lazy val library = project.settings(uniqueName)

def uniqueName = 
	name := (name.value + "-" + randomSuffix( (baseDirectory in ThisBuild).value))

// better long-term approach to a clean cache/local
//  would be to not use the actual ~/.m2/repository
def randomSuffix(base: File) = {
	// need to persist it so that it doesn't change across reloads
	val persist = base / "suffix"
	if(persist.exists)
		IO.read(persist)
	else {
		val s = Hash.halfHashString(System.currentTimeMillis.toString)
		IO.write(persist, s)
		s
	}
}
