retrieveManaged := true

libraryDependencies += "log4j" % "log4j" % "1.2.16"

autoScalaLibrary := false

managedDirectory := file("dependencies")

retrievePattern := "[artifact]-[revision](-[classifier]).[ext]"