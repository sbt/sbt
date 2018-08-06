lazy val root = (project in file(".")).
  settings(inThisBuild(List(
      organization := "A",
      version := "1.0",
      ivyPaths := baseDirectory( dir => IvyPaths(dir, Some(dir / "ivy" / "cache")) ).value,
      externalResolvers := (baseDirectory map { base => Resolver.file("local", base / "ivy" / "local" asFile)(Resolver.ivyStylePatterns) :: Nil }).value
    )),
    mavenStyle,
    name := "Retrieve Test",
    libraryDependencies := (publishMavenStyle { style => if(style) mavenStyleDependencies else ivyStyleDependencies }).value
  )


lazy val mavenStyle = publishMavenStyle := (baseDirectory { base => (base / "mavenStyle") exists }).value

def ivyStyleDependencies = parentDep("A") :: subDep("A") :: subDep("B") ::parentDep("D") :: Nil
def mavenStyleDependencies = parentDep("B") :: parentDep("C") :: subDep("C") :: subDep("D") :: Nil

def parentDep(org: String) =  org %% "publish-test" % "1.0"
def subDep(org: String) = org %% "sub-project" % "1.0"
