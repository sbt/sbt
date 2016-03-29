lazy val parent = (project in file(".")).
  aggregate(core, reporters).
  settings(
    name := "Flowmodel"
  )

lazy val core = (project in file("core")).
  settings(
    name := "Flowmodel-core"
  )

lazy val reporters = (project in file("reporters")).
  aggregate(jfreechart).
  dependsOn(jfreechart).
  settings(
    name := "Extra-reporters"
  )

lazy val jfreechart = (project in file("jfreechart")).
  dependsOn(core).
  settings(
    name := "JFreeChart-reporters"
  )
