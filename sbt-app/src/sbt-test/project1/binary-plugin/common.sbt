ThisBuild / organization := "org.example"

// We have to use snapshot because this is publishing to our local ivy cache instead of
// an integration cache, so we're in danger land.
ThisBuild / version := "3.4-SNAPSHOT"
