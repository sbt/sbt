lazy val checkPom = taskKey[Unit]("check pom emits <type> for non-jar explicit artifacts")

lazy val root = (project in file("."))
  .settings(
    scalaVersion := "2.13.16",
    autoScalaLibrary := false,
    libraryDependencies += "org.eclipse.jetty" % "jetty-webapp" % "11.0.15" artifacts (Artifact("jetty-webapp", "war", "war")),
    libraryDependencies += "com.typesafe" % "config" % "1.4.3",
    checkPom := {
      val converter = fileConverter.value
      val pomFile = makePom.value
      val pom = xml.XML.loadFile(converter.toPath(pomFile).toFile)
      val deps = pom \ "dependencies" \ "dependency"

      // WAR dependency should be present with <type>war</type>
      val warDep = deps.find(d => (d \ "artifactId").text == "jetty-webapp")
      assert(warDep.isDefined, s"jetty-webapp dependency missing from POM.\nDeps: ${deps.map(d => (d \ "artifactId").text)}")
      val warType = (warDep.get \ "type").text
      assert(warType == "war", s"Expected <type>war</type> for jetty-webapp, got: '$warType'")

      // JAR dependency should NOT have <type> (jar is the Maven default)
      val jarDep = deps.find(d => (d \ "artifactId").text == "config")
      assert(jarDep.isDefined, "config dependency missing from POM")
      val jarType = (jarDep.get \ "type").text
      assert(jarType == "", s"Expected no <type> for config (jar is default), got: '$jarType'")
    },
  )
