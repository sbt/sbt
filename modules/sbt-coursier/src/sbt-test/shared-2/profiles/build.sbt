scalaVersion := "2.12.8"

libraryDependencies += "org.apache.spark" %% "spark-sql" % "2.4.3"

mavenProfiles += "hadoop-3.1"
