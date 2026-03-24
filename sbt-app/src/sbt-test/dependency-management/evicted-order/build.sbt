@transient
lazy val checkEvictedOrder = taskKey[Unit]("check evicted output is sorted")

lazy val root = (project in file("."))
  .settings(
    scalaVersion := "2.13.16",
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-actor" % "2.6.20",
      "com.typesafe.akka" %% "akka-stream" % "2.6.19",
      "io.circe" %% "circe-core" % "0.14.5",
      "io.circe" %% "circe-parser" % "0.14.3",
    ),
    checkEvictedOrder := {
      val pub = Keys.publisher.value
      val module = pub.moduleDescriptor(
        moduleSettings.value.asInstanceOf[sbt.librarymanagement.ModuleDescriptorConfiguration]
      )
      val report = update.value
      val ew = sbt.librarymanagement.EvictionWarning(
        module,
        sbt.librarymanagement.EvictionWarningOptions.full.withShowCallers(false),
        report
      )
      val allOrgs = ew.allEvictions.map(p => s"${p.organization}:${p.name}")
      val sorted = allOrgs.sorted
      assert(allOrgs == sorted, s"Evictions not sorted.\nGot:      $allOrgs\nExpected: $sorted")
    },
  )
