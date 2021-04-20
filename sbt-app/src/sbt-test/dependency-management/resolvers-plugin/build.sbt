lazy val check = taskKey[Unit]("")
ThisBuild / includePluginResolvers := true

check := {
  val rs = fullResolvers.value
  assert(rs exists (_.name == "bintray-eed3si9n-sbt-plugins"), s"$rs does not include bintray")
}
