lazy val root = project in file(".")

lazy val js = project

def bigDataSesameVersion = "2.6.10"

libraryDependencies += "foo" % "bar" % bigDataSesameVersion