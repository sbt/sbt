val specsJunit = "org.specs2" %% "specs2-junit" % "4.3.4"
val junitinterface = "com.novocode" % "junit-interface" % "0.11"
ThisBuild / scalaVersion := "2.12.20"
libraryDependencies += junitinterface % Test
libraryDependencies += specsJunit % Test
