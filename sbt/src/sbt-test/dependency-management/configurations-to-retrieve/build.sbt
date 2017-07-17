configurationsToRetrieve := Some(Vector(Compile))

retrieveManaged := true

libraryDependencies += "log4j" % "log4j" % "1.2.16" % "compile"

autoScalaLibrary := false

managedDirectory := file("dependencies")

retrievePattern := "[conf]/[artifact]-[revision](-[classifier]).[ext]"
