scalaVersion := "2.13.1"
csrConfiguration := csrConfiguration.value.withCache(target.value / "coursier-cache")

libraryDependencies += "com.typesafe.play" %% "play-test" % "2.8.0-RC1" % Test // worked around in 2.8.0
libraryDependencies += "org.scalatest"     %% "scalatest" % "3.0.8"     % Test
