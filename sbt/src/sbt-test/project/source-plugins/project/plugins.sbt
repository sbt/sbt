lazy val plugins = (project in file("."))
  .dependsOn(proguard, git)

// e7b4732969c137db1b5
// d4974f7362bf55d3f52
lazy val proguard = uri("git://github.com/sbt/sbt-proguard.git#e7b4732969c137db1b5")
lazy val git = uri("git://github.com/sbt/sbt-git.git#2e7c2503850698d60bb")
