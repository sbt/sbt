lazy val stable = settingKey[Boolean]("Build stable release (true) or not (false).")

stable := (baseDirectory.value / "make-stable").exists

name := "def"

sbtPlugin := true

organization := "org.example"

version := ( if(stable.value) "1.0" else "1.1-SNAPSHOT" )

publishTo := {
	val base = baseDirectory.value / ( if(stable.value) "stable" else "snapshot" )
	Some( Resolver.file("local-" + base, base)(Resolver.ivyStylePatterns) )
}

publishMavenStyle := false
