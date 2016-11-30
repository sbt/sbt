lazy val root = (project in file(".")).
  dependsOn(sub).
  aggregate(sub).
  settings(inThisBuild(List(
      organization := "A",
      version := "1.0",
      ivyPaths := baseDirectory( dir => IvyPaths(dir, Some(dir / "ivy" / "cache")) ).value,
      externalResolvers := (baseDirectory map { base => Resolver.file("local", base / "ivy" / "local" asFile)(Resolver.ivyStylePatterns) :: Nil }).value
    )),
    mavenStyle,
    interProject,
    name := "Publish Test"
  )

lazy val sub = project.
  settings(
    mavenStyle,
    name := "Sub Project"
  )

lazy val mavenStyle = publishMavenStyle := (baseDirectory { base => (base / "mavenStyle") exists }).value

def interProject =
  projectDependencies := ((publishMavenStyle, publishMavenStyle in sub, projectDependencies) map { (style, subStyle, pd) => if(style == subStyle) pd else Nil }).value
