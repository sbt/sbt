lazy val a = (project in file(".")).
  settings(externalIvySettings()) dependsOn(b)

lazy val b = (project in file("b")).
  settings(externalIvySettings( (baseDirectory in ThisBuild) / "ivysettings.xml" ))
