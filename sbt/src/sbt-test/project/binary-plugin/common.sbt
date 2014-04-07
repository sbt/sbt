organization in ThisBuild := "org.example"

// We have to use snapshot because this is publishing to our local ivy cache instead of
// an integration cache, so we're in danger land.
version in ThisBuild := "3.4-SNAPSHOT"


