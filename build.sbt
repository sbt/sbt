import Dependencies._
import Path._
import com.typesafe.tools.mima.core._, ProblemFilters._

def commonSettings: Seq[Setting[_]] = Def settings (
  scalaVersion := scala212,
  // publishArtifact in packageDoc := false,
  resolvers += Resolver.typesafeIvyRepo("releases"),
  resolvers += Resolver.sonatypeRepo("snapshots"),
  resolvers += "bintray-sbt-maven-releases" at "https://dl.bintray.com/sbt/maven-releases/",
  // concurrentRestrictions in Global += Util.testExclusiveRestriction,
  testOptions += Tests.Argument(TestFrameworks.ScalaCheck, "-w", "1"),
  javacOptions in compile ++= Seq("-Xlint", "-Xlint:-serial"),
  crossScalaVersions := Seq(scala211, scala212),
  resolvers += Resolver.sonatypeRepo("public"),
  scalacOptions := {
    val old = scalacOptions.value
    scalaVersion.value match {
      case sv if sv.startsWith("2.10") =>
        old diff List("-Xfuture", "-Ywarn-unused", "-Ywarn-unused-import")
      case sv if sv.startsWith("2.11") => old ++ List("-Ywarn-unused", "-Ywarn-unused-import")
      case _                           => old ++ List("-Ywarn-unused", "-Ywarn-unused-import", "-YdisableFlatCpCaching")
    }
  },
  inCompileAndTest(scalacOptions in console --= Vector("-Ywarn-unused-import", "-Ywarn-unused", "-Xlint")),
  publishArtifact in Compile := true,
  publishArtifact in Test := false,
  parallelExecution in Test := false
)

val mimaSettings = Def settings (
  mimaPreviousArtifacts := Set(
    "1.0.0", "1.0.1", "1.0.2", "1.0.3", "1.0.4",
    "1.1.0", "1.1.1", "1.1.2", "1.1.3",
  ) map (version =>
    organization.value %% moduleName.value % version
      cross (if (crossPaths.value) CrossVersion.binary else CrossVersion.disabled)
  ),
)

lazy val lmRoot = (project in file("."))
  .aggregate(lmCore, lmIvy)
  .settings(
    inThisBuild(
      Seq(
        homepage := Some(url("https://github.com/sbt/librarymanagement")),
        description := "Library management module for sbt",
        scmInfo := {
          val slug = "sbt/librarymanagement"
          Some(ScmInfo(url(s"https://github.com/$slug"), s"git@github.com:$slug.git"))
        },
        bintrayPackage := "librarymanagement",
        scalafmtOnCompile in Sbt := false,
        git.baseVersion := "1.2.0",
        version := {
          val v = version.value
          if (v contains "SNAPSHOT") git.baseVersion.value
          else v
        }
      )),
    commonSettings,
    name := "LM Root",
    publish := {},
    publishLocal := {},
    publishArtifact in Compile := false,
    publishArtifact := false,
    customCommands
  )

lazy val lmCore = (project in file("core"))
  .enablePlugins(ContrabandPlugin, JsonCodecPlugin)
  .settings(
    commonSettings,
    name := "librarymanagement-core",
    libraryDependencies ++= Seq(
      jsch,
      scalaReflect.value,
      scalaCompiler.value,
      launcherInterface,
      gigahorseOkhttp,
      okhttpUrlconnection,
      sjsonnewScalaJson.value % Optional,
      scalaTest % Test,
      scalaCheck % Test
    ),
    libraryDependencies ++= scalaXml.value,
    resourceGenerators in Compile += Def
      .task(
        Util.generateVersionFile(
          version.value,
          resourceManaged.value,
          streams.value,
          (compile in Compile).value
        )
      )
      .taskValue,
    managedSourceDirectories in Compile +=
      baseDirectory.value / "src" / "main" / "contraband-scala",
    sourceManaged in (Compile, generateContrabands) := baseDirectory.value / "src" / "main" / "contraband-scala",
    contrabandFormatsForType in generateContrabands in Compile := DatatypeConfig.getFormats,
    // WORKAROUND sbt/sbt#2205 include managed sources in packageSrc
    mappings in (Compile, packageSrc) ++= {
      val srcs = (managedSources in Compile).value
      val sdirs = (managedSourceDirectories in Compile).value
      val base = baseDirectory.value
      (((srcs --- sdirs --- base) pair (relativeTo(sdirs) | relativeTo(base) | flat)) toSeq)
    },
    mimaSettings,
    mimaBinaryIssueFilters ++= Seq(
      exclude[DirectMissingMethodProblem]("sbt.librarymanagement.EvictionWarningOptions.this"),
      exclude[DirectMissingMethodProblem]("sbt.librarymanagement.EvictionWarningOptions.copy"),
      exclude[IncompatibleResultTypeProblem]("sbt.librarymanagement.EvictionWarningOptions.copy$default$7"),

      // internal class moved
      exclude[MissingClassProblem]("sbt.internal.librarymanagement.InlineConfigurationFunctions"),
      // dropped internal class parent (InlineConfigurationFunctions)
      exclude[MissingTypesProblem]("sbt.librarymanagement.ModuleDescriptorConfiguration$"),

      // Configuration's copy method was never meant to be public
      exclude[DirectMissingMethodProblem]("sbt.librarymanagement.Configuration.copy"),
      exclude[DirectMissingMethodProblem]("sbt.librarymanagement.Configuration.copy$default$*"),

      // the data type copy methods were never meant to be public
      exclude[DirectMissingMethodProblem]("sbt.librarymanagement.ArtifactExtra.copy"),
      exclude[DirectMissingMethodProblem]("sbt.librarymanagement.ArtifactExtra.copy$default$*"),
      exclude[DirectMissingMethodProblem]("sbt.librarymanagement.ModuleReportExtra.copy"),
      exclude[DirectMissingMethodProblem]("sbt.librarymanagement.ModuleReportExtra.copy$default$*"),
      exclude[DirectMissingMethodProblem]("sbt.librarymanagement.ArtifactTypeFilterExtra.copy"),
      exclude[DirectMissingMethodProblem]("sbt.librarymanagement.ArtifactTypeFilterExtra.copy$default$*"),
      exclude[DirectMissingMethodProblem]("sbt.librarymanagement.ModuleIDExtra.copy"),
      exclude[DirectMissingMethodProblem]("sbt.librarymanagement.ModuleIDExtra.copy$default$*"),

      // these abstract classes are private[librarymanagement] so it's fine if they have more methods
      exclude[ReversedMissingMethodProblem]("sbt.librarymanagement.ArtifactExtra.*"),
      exclude[ReversedMissingMethodProblem]("sbt.librarymanagement.ModuleReportExtra.*"),
      exclude[ReversedMissingMethodProblem]("sbt.librarymanagement.ArtifactTypeFilterExtra.*"),
      exclude[ReversedMissingMethodProblem]("sbt.librarymanagement.ModuleIDExtra.*"),

      // these abstract classes are private[librarymanagement] so they can lose these abstract methods
      exclude[DirectMissingMethodProblem]("sbt.librarymanagement.ArtifactExtra.type"),
      exclude[DirectMissingMethodProblem]("sbt.librarymanagement.ArtifactExtra.url"),
      exclude[DirectMissingMethodProblem]("sbt.librarymanagement.ArtifactExtra.checksum"),
      exclude[DirectMissingMethodProblem]("sbt.librarymanagement.ArtifactExtra.name"),
      exclude[DirectMissingMethodProblem]("sbt.librarymanagement.ArtifactExtra.configurations"),
      exclude[DirectMissingMethodProblem]("sbt.librarymanagement.ArtifactExtra.classifier"),
      exclude[DirectMissingMethodProblem]("sbt.librarymanagement.ArtifactExtra.extension"),
      exclude[DirectMissingMethodProblem]("sbt.librarymanagement.ArtifactTypeFilterExtra.types"),

      // contraband issue
      exclude[DirectMissingMethodProblem]("sbt.internal.librarymanagement.ConfigurationReportLite.copy*"),
      exclude[DirectMissingMethodProblem]("sbt.internal.librarymanagement.UpdateReportLite.copy*"),
      exclude[DirectMissingMethodProblem]("sbt.librarymanagement.Artifact.copy*"),
      exclude[DirectMissingMethodProblem]("sbt.librarymanagement.ArtifactTypeFilter.copy*"),
      exclude[DirectMissingMethodProblem]("sbt.librarymanagement.Binary.copy*"),
      exclude[DirectMissingMethodProblem]("sbt.librarymanagement.Caller.copy*"),
      exclude[DirectMissingMethodProblem]("sbt.librarymanagement.ChainedResolver.copy*"),
      exclude[DirectMissingMethodProblem]("sbt.librarymanagement.Checksum.copy*"),
      exclude[DirectMissingMethodProblem]("sbt.librarymanagement.ConfigRef.copy*"),
      exclude[DirectMissingMethodProblem]("sbt.librarymanagement.ConfigurationReport.copy*"),
      exclude[DirectMissingMethodProblem]("sbt.librarymanagement.ConflictManager.copy*"),
      exclude[DirectMissingMethodProblem]("sbt.librarymanagement.Constant.copy*"),
      exclude[DirectMissingMethodProblem]("sbt.librarymanagement.Developer.copy*"),
      exclude[DirectMissingMethodProblem]("sbt.librarymanagement.Disabled.copy*"),
      exclude[DirectMissingMethodProblem]("sbt.librarymanagement.FileConfiguration.copy*"),
      exclude[DirectMissingMethodProblem]("sbt.librarymanagement.FileRepository.copy*"),
      exclude[DirectMissingMethodProblem]("sbt.librarymanagement.Full.copy*"),
      exclude[DirectMissingMethodProblem]("sbt.librarymanagement.GetClassifiersConfiguration.copy*"),
      exclude[DirectMissingMethodProblem]("sbt.librarymanagement.GetClassifiersModule.copy*"),
      exclude[DirectMissingMethodProblem]("sbt.librarymanagement.InclExclRule.copy*"),
      exclude[DirectMissingMethodProblem]("sbt.librarymanagement.IvyFileConfiguration.copy*"),
      exclude[DirectMissingMethodProblem]("sbt.librarymanagement.KeyFileAuthentication.copy*"),
      exclude[DirectMissingMethodProblem]("sbt.librarymanagement.MakePomConfiguration.copy*"),
      exclude[DirectMissingMethodProblem]("sbt.librarymanagement.MavenCache.copy*"),
      exclude[DirectMissingMethodProblem]("sbt.librarymanagement.MavenRepo.copy*"),
      exclude[DirectMissingMethodProblem]("sbt.librarymanagement.ModuleConfiguration.copy*"),
      exclude[DirectMissingMethodProblem]("sbt.librarymanagement.ModuleDescriptorConfiguration.copy*"),
      exclude[DirectMissingMethodProblem]("sbt.librarymanagement.ModuleID.copy*"),
      exclude[DirectMissingMethodProblem]("sbt.librarymanagement.ModuleInfo.copy*"),
      exclude[DirectMissingMethodProblem]("sbt.librarymanagement.ModuleReport.copy*"),
      exclude[DirectMissingMethodProblem]("sbt.librarymanagement.OrganizationArtifactReport.copy*"),
      exclude[DirectMissingMethodProblem]("sbt.librarymanagement.PasswordAuthentication.copy*"),
      exclude[DirectMissingMethodProblem]("sbt.librarymanagement.Patch.copy*"),
      exclude[DirectMissingMethodProblem]("sbt.librarymanagement.Patterns.copy*"),
      exclude[DirectMissingMethodProblem]("sbt.librarymanagement.PomConfiguration.copy*"),
      exclude[DirectMissingMethodProblem]("sbt.librarymanagement.PublishConfiguration.copy*"),
      exclude[DirectMissingMethodProblem]("sbt.librarymanagement.RetrieveConfiguration.copy*"),
      exclude[DirectMissingMethodProblem]("sbt.librarymanagement.ScalaModuleInfo.copy*"),
      exclude[DirectMissingMethodProblem]("sbt.librarymanagement.ScmInfo.copy*"),
      exclude[DirectMissingMethodProblem]("sbt.librarymanagement.SftpRepository.copy*"),
      exclude[DirectMissingMethodProblem]("sbt.librarymanagement.SshConnection.copy*"),
      exclude[DirectMissingMethodProblem]("sbt.librarymanagement.SshRepository.copy*"),
      exclude[DirectMissingMethodProblem]("sbt.librarymanagement.UpdateConfiguration.copy*"),
      exclude[DirectMissingMethodProblem]("sbt.librarymanagement.UpdateReport.copy*"),
      exclude[DirectMissingMethodProblem]("sbt.librarymanagement.UpdateStats.copy*"),
      exclude[DirectMissingMethodProblem]("sbt.librarymanagement.URLRepository.copy*"),
    ),
  )
  .configure(addSbtIO, addSbtUtilLogging, addSbtUtilPosition, addSbtUtilCache)

lazy val lmCommonTest = (project in file("common-test"))
  .dependsOn(lmCore)
  .settings(
    commonSettings,
    skip in publish := true,
    name := "common-test",
    libraryDependencies ++= Seq(scalaTest, scalaCheck),
    scalacOptions in (Compile, console) --=
      Vector("-Ywarn-unused-import", "-Ywarn-unused", "-Xlint"),
    mimaSettings,
  )

lazy val lmIvy = (project in file("ivy"))
  .enablePlugins(ContrabandPlugin, JsonCodecPlugin)
  .dependsOn(lmCore, lmCommonTest % Test)
  .settings(
    commonSettings,
    name := "librarymanagement-ivy",
    libraryDependencies ++= Seq(ivy, scalaTest % Test, scalaCheck % Test),
    managedSourceDirectories in Compile +=
      baseDirectory.value / "src" / "main" / "contraband-scala",
    sourceManaged in (Compile, generateContrabands) := baseDirectory.value / "src" / "main" / "contraband-scala",
    contrabandFormatsForType in generateContrabands in Compile := DatatypeConfig.getFormats,
    scalacOptions in (Compile, console) --=
      Vector("-Ywarn-unused-import", "-Ywarn-unused", "-Xlint"),
    mimaSettings,
    mimaBinaryIssueFilters ++= Seq(
      exclude[DirectMissingMethodProblem]("sbt.internal.librarymanagement.ivyint.GigahorseUrlHandler#SbtUrlInfo.this"),
      exclude[IncompatibleMethTypeProblem]("sbt.internal.librarymanagement.ivyint.GigahorseUrlHandler#SbtUrlInfo.this"),
      exclude[DirectMissingMethodProblem]("sbt.internal.librarymanagement.ivyint.GigahorseUrlHandler.checkStatusCode"),

      // sbt.internal methods that changed type signatures and aren't used elsewhere in production code
      exclude[DirectMissingMethodProblem]("sbt.internal.librarymanagement.IvySbt#ParallelCachedResolutionResolveEngine.mergeErrors"),
      exclude[DirectMissingMethodProblem]("sbt.internal.librarymanagement.IvySbt.cleanCachedResolutionCache"),
      exclude[DirectMissingMethodProblem]("sbt.internal.librarymanagement.IvyRetrieve.artifacts"),
      exclude[DirectMissingMethodProblem]("sbt.internal.librarymanagement.IvyScalaUtil.checkModule"),
      exclude[DirectMissingMethodProblem]("sbt.internal.librarymanagement.ivyint.CachedResolutionResolveEngine.mergeErrors"),
      exclude[DirectMissingMethodProblem]("sbt.internal.librarymanagement.ivyint.CachedResolutionResolveCache.buildArtificialModuleDescriptor"),
      exclude[DirectMissingMethodProblem]("sbt.internal.librarymanagement.ivyint.CachedResolutionResolveCache.buildArtificialModuleDescriptors"),
      exclude[ReversedMissingMethodProblem]("sbt.internal.librarymanagement.ivyint.CachedResolutionResolveEngine.mergeErrors"),

      exclude[DirectMissingMethodProblem]("sbt.internal.librarymanagement.ivyint.GigahorseUrlHandler#SbtUrlInfo.this"),
      exclude[IncompatibleMethTypeProblem]("sbt.internal.librarymanagement.ivyint.GigahorseUrlHandler#SbtUrlInfo.this"),
      exclude[DirectMissingMethodProblem]("sbt.internal.librarymanagement.ivyint.GigahorseUrlHandler.checkStatusCode"),

      // contraband issue
      exclude[DirectMissingMethodProblem]("sbt.librarymanagement.ivy.ExternalIvyConfiguration.copy*"),
      exclude[DirectMissingMethodProblem]("sbt.librarymanagement.ivy.InlineIvyConfiguration.copy*"),
      exclude[DirectMissingMethodProblem]("sbt.librarymanagement.ivy.IvyPaths.copy*"),
    ),
  )

def customCommands: Seq[Setting[_]] = Seq(
  commands += Command.command("release") { state =>
    // "clean" ::
    "+compile" ::
      "+publishSigned" ::
      "reload" ::
      state
  }
)

inThisBuild(Seq(
  whitesourceProduct                   := "Lightbend Reactive Platform",
  whitesourceAggregateProjectName      := "sbt-lm-master",
  whitesourceAggregateProjectToken     := "9bde4ccbaab7401a91f8cda337af84365d379e13abaf473b85cb16e3f5c65cb6",
  whitesourceIgnoredScopes             += "scalafmt",
  whitesourceFailOnError               := sys.env.contains("WHITESOURCE_PASSWORD"), // fail if pwd is present
  whitesourceForceCheckAllDependencies := true,
))

def inCompileAndTest(ss: SettingsDefinition*): Seq[Setting[_]] =
  Seq(Compile, Test) flatMap (inConfig(_)(Def.settings(ss: _*)))
