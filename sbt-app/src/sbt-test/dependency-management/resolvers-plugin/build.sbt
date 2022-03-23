lazy val check = taskKey[Unit]("")
ThisBuild / includePluginResolvers := true

check := {
  val ivy = fullResolvers.value
  assert(ivy exists (_.name == "bintray-eed3si9n-sbt-plugins"), s"$ivy does not include bintray")

  val cs = csrResolvers.value
  assert(cs exists (_.name == "bintray-eed3si9n-sbt-plugins"), s"$cs does not include bintray")
}
