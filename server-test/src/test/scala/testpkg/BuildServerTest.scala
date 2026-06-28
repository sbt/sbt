/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package testpkg

import sbt.internal.bsp.*
import sbt.internal.bsp.codec.JsonProtocol.given
import sbt.internal.langserver.{ ErrorCodes, LogMessageParams }
import sbt.internal.langserver.codec.JsonProtocol.given
import sbt.internal.protocol.JsonRpcNotificationMessage
import sbt.IO
import sjsonnew.JsonWriter
import sjsonnew.support.scalajson.unsafe.{ CompactPrinter, Converter }

import java.io.File
import java.net.URI
import java.nio.file.{ Files, Paths }
import java.util.concurrent.TimeoutException
import scala.concurrent.duration.*
import scala.util.{ Failure, Success }

// starts svr using server-test/buildserver and perform custom server tests
class BuildServerTest extends AbstractServerTest {

  override val testDirectory: String = "buildserver"

  test("build/initialize") {
    val id = initializeRequest()
    val res = svr.session.waitForResultInResponseMsg[InitializeBuildResult](10.seconds, id).get
    assert(res.capabilities.resourcesProvider.contains(true))
    assert(res.capabilities.outputPathsProvider.contains(true))
  }

  test("workspace/buildTargets") {
    val id = sendRequest("workspace/buildTargets")
    val result =
      svr.session.waitForResultInResponseMsg[WorkspaceBuildTargetsResult](10.seconds, id).get
    val utilTargetIdentifier = BuildTargetIdentifier(buildTargetUri("util", "Compile"))
    val utilTarget = result.targets.find(_.id == utilTargetIdentifier).get
    assert(utilTarget.id.uri.toString.endsWith("#util/Compile"))
    val runAndTestTarget = result.targets.find(_.displayName.contains("runAndTest")).get
    // runAndTest should declare the dependency to util even if optional
    assert(runAndTestTarget.dependencies.contains(utilTargetIdentifier))
    val buildServerBuildTarget =
      result.targets.find(_.displayName.contains("buildserver-root-build")) match
        case Some(t) => t
        case None    => sys.error(s"buildserver-root-build not in ${result.targets}")
    assert(buildServerBuildTarget.id.uri.toString.endsWith("#buildserver-root-build"))
    assert(!result.targets.exists(_.displayName.contains("badBuildTarget")))
    // Check for JVM based Scala Project, built target should contain Java version information
    val scalaBuildTarget =
      Converter.fromJsonOptionUnsafe[ScalaBuildTarget](utilTarget.data)
    val javaTarget = scalaBuildTarget.jvmBuildTarget
    (javaTarget.flatMap(_.javaVersion), javaTarget.flatMap(_.javaHome)) match {
      case (Some(javaVersion), Some(javaHome)) =>
        assert(javaVersion.equals(sys.props("java.version")))
        assert(javaHome.equals(Paths.get(sys.props("java.home")).toUri))
      case _ => fail("JVM build target should contain javaVersion and javaHome")
    }
  }

  test("buildTarget/sources") {
    val buildTarget = buildTargetUri("util", "Compile")
    val badBuildTarget = buildTargetUri("badBuildTarget", "Compile")
    buildTargetSources(Seq(buildTarget, badBuildTarget))
    val s = svr.session.waitForResultInResponseMsg[SourcesResult](10.seconds)(_ => true).get
    val sources = s.items.head.sources.map(_.uri)
    assert(sources.contains(new File(svr.baseDirectory, "util/src/main/scala").toURI))
  }

  test("buildTarget/sources: base sources") {
    val buildTarget = buildTargetUri("buildserver-root", "Compile")
    buildTargetSources(Seq(buildTarget))
    val s = svr.session.waitForResultInResponseMsg[SourcesResult](10.seconds)(_ => true).get
    val sources = s.items.head.sources
    val expectedSource = SourceItem(
      new File(svr.baseDirectory, "BaseSource.scala").toURI,
      SourceItemKind.File,
      generated = false
    )
    assert(sources.contains(expectedSource))
  }

  test("buildTarget/sources: sbt") {
    val x = new URI(s"${svr.baseDirectory.getAbsoluteFile.toURI}#buildserver-root-build")
    buildTargetSources(Seq(x))
    val s = svr.session.waitForResultInResponseMsg[SourcesResult](10.seconds)(_ => true).get
    val sources = s.items.head.sources.map(_.uri).sorted
    val expectedSources = Vector(
      "build.sbt",
      "project/A.scala",
      "project/src/main/java",
      "project/src/main/scala-3",
      "project/src/main/scala-sbt-2",
      "project/src/main/scala/",
      s"target/out/jvm/scala-${TestProperties.scalaVersion}/buildserver-build/src_managed/main"
    ).map(rel => new File(svr.baseDirectory.getAbsoluteFile, rel).toURI).sorted
    assert(sources == expectedSources)
  }

  test("buildTarget/compile") {
    val id = compile(buildTargetUri("util", "Compile"))
    val res = svr.session.waitForResultInResponseMsg[BspCompileResult](10.seconds, id).get
    assert(res.statusCode == StatusCode.Success)
  }

  test("buildTarget/compile - reports compilation progress") {
    val buildTarget = buildTargetUri("runAndTest", "Compile")
    compile(buildTarget)
    // This doesn't always come back in 10s on CI.
    svr.session
      .waitForParamsInNotificationMsg[TaskStartParams](20.seconds) { p =>
        p.message.contains("Compiling runAndTest")
      }
      .get

    svr.session
      .waitForParamsInNotificationMsg[TaskProgressParams](20.seconds) { p =>
        p.message.contains("Compiling runAndTest (15%)")
      }
      .get

    svr.session
      .waitForParamsInNotificationMsg[TaskProgressParams](20.seconds) { p =>
        p.message.contains("Compiling runAndTest (100%)")
      }
      .get

    svr.session
      .waitForParamsInNotificationMsg[PublishDiagnosticsParams](20.seconds) { p =>
        p.diagnostics.isEmpty
      }
      .get

    svr.session
      .waitForParamsInNotificationMsg[TaskFinishParams](20.seconds) { p =>
        p.message.contains("Compiled runAndTest")
      }
      .get
  }

  test(
    "buildTarget/compile [diagnostics] don't publish unnecessary for successful compilation case"
  ) {
    val buildTarget = buildTargetUri("diagnostics", "Compile")
    val mainFile = new File(svr.baseDirectory, "diagnostics/src/main/scala/Diagnostics.scala")

    compile(buildTarget)

    svr.session
      .waitForParamsInNotificationMsg[TaskFinishParams](30.seconds) { p =>
        p.message.contains("Compiled diagnostics")
      }
      .get

    // introduce compile error
    IO.write(
      mainFile,
      """|object Diagnostics {
         |  private val a: Int = ""
         |}""".stripMargin
    )

    compile(buildTarget)

    svr.session
      .waitForParamsInNotificationMsg[PublishDiagnosticsParams](30.seconds) { p =>
        p.textDocument.uri.toString.contains("Diagnostics.scala") &&
        p.diagnostics.exists(_.message.contains("type mismatch"))
      }
      .get

    // fix compilation error
    IO.write(
      mainFile,
      """|object Diagnostics {
         |  private val a: Int = 5
         |}""".stripMargin
    )

    reloadWorkspace()
    compile(buildTarget)

    svr.session
      .waitForParamsInNotificationMsg[PublishDiagnosticsParams](30.seconds) { p =>
        p.textDocument.uri.toString.contains("Diagnostics.scala") &&
        p.diagnostics.isEmpty
      }
      .get

    svr.session
      .waitForParamsInNotificationMsg[TaskFinishParams](10.seconds) { p =>
        p.data.exists(d => CompactPrinter(d).contains("\"noOp\":true"))
      }
      .get

    // trigger no-op compilation
    compile(buildTarget)

    svr.session
      .waitForNotificationMsg(20.seconds) { n =>
        n.method match {
          case "build/publishDiagnostics" =>
            n.params.flatMap(Converter.fromJson[PublishDiagnosticsParams](_).toOption).foreach {
              p =>
                if (p.textDocument.uri.toString.contains("Diagnostics.scala"))
                  throw new Exception("shouldn't send publishDiagnostics if noOp compilation")
            }
            false
          case "build/taskFinish" =>
            n.params
              .flatMap(Converter.fromJson[TaskFinishParams](_).toOption)
              .exists(_.data.exists(d => CompactPrinter(d).contains("\"noOp\":true")))
          case _ => false
        }
      }
      .get
  }

  test("buildTarget/compile [diagnostics] clear stale warnings") {
    val buildTarget = buildTargetUri("diagnostics", "Compile")
    val testFile = new File(svr.baseDirectory, s"diagnostics/src/main/scala/PatternMatch.scala")

    compile(buildTarget)

    svr.session
      .waitForParamsInNotificationMsg[PublishDiagnosticsParams](30.seconds) { p =>
        p.textDocument.uri.toString.contains("PatternMatch.scala") &&
        p.diagnostics.exists(_.message.contains("match may not be exhaustive"))
      }
      .get

    IO.write(
      testFile,
      """|class PatternMatch {
         |  val opt: Option[Int] = None
         |  opt match {
         |    case Some(value) => ()
         |    case None => ()
         |  }
         |}
         |""".stripMargin
    )

    reloadWorkspace()
    compile(buildTarget)

    svr.session
      .waitForParamsInNotificationMsg[PublishDiagnosticsParams](30.seconds) { p =>
        p.textDocument.uri.toString.contains("PatternMatch.scala") &&
        p.diagnostics.isEmpty
      }
      .get
  }

  test("buildTarget/compile - returns StatusCode.Error when compilation fails") {
    // Reproduces #8104: failed BSP compile must return BspCompileResult with statusCode Error,
    // not a JSON-RPC error. Placed after diagnostics tests so shared message queue is not polluted.
    val buildTarget = buildTargetUri("diagnostics", "Compile")
    val mainFile = new File(svr.baseDirectory, "diagnostics/src/main/scala/Diagnostics.scala")
    val original = IO.read(mainFile)
    try {
      IO.write(
        mainFile,
        """|object Diagnostics {
           |  private val a: Int = ""
           |}""".stripMargin
      )
      val compileId = compile(buildTarget)
      val res = svr.session.waitForResultInResponseMsg[BspCompileResult](30.seconds, compileId).get
      assert(
        res.statusCode == StatusCode.Error,
        s"expected StatusCode.Error, got ${res.statusCode}"
      )
    } finally {
      IO.write(mainFile, original)
    }
  }

  // 1. Cause a real compile error and observe non-empty diagnostics.
  // 2. Request buildTarget/scalaMainClasses.
  // 3. Watch any notifications emitted while that request is processed.
  // 4. Fail if one of them is the forbidden empty diagnostic reset.
  test("buildTarget/scalaMainClasses does not clear compile diagnostics (#9345)") {
    def isForbiddenDiagnosticReset(n: JsonRpcNotificationMessage): Boolean =
      n.method == "build/publishDiagnostics" &&
        n.params
          .flatMap(Converter.fromJson[PublishDiagnosticsParams](_).toOption)
          .exists(p =>
            p.textDocument.uri.toString.contains("Diagnostics.scala") &&
              p.reset &&
              p.diagnostics.isEmpty
          )

    def failIfForbiddenDiagnosticReset(n: JsonRpcNotificationMessage): Unit =
      if (isForbiddenDiagnosticReset(n))
        fail(
          "buildTarget/scalaMainClasses must not publish empty reset=true " +
            "diagnostics for Diagnostics.scala after a failed compile (#9345)"
        )

    def drainQueuedNotificationsAndFailOnForbiddenReset(): Unit =
      svr.session.waitForNotificationMsg(Duration.Zero)(_ => true) match {
        case Success(n) =>
          failIfForbiddenDiagnosticReset(n)
          drainQueuedNotificationsAndFailOnForbiddenReset()
        case Failure(_: TimeoutException) => ()
        case Failure(e)                   => throw e
      }

    val buildTarget = buildTargetUri("diagnostics", "Compile")
    val mainFile = new File(svr.baseDirectory, "diagnostics/src/main/scala/Diagnostics.scala")
    val original = IO.read(mainFile)
    try {
      IO.write(
        mainFile,
        """|object Diagnostics {
           |  private val a: Int = ""
           |}""".stripMargin
      )

      val compileId = compile(buildTarget)
      val res = svr.session.waitForResultInResponseMsg[BspCompileResult](30.seconds, compileId).get
      assert(
        res.statusCode == StatusCode.Error,
        s"expected StatusCode.Error, got ${res.statusCode}"
      )

      svr.session
        .waitForParamsInNotificationMsg[PublishDiagnosticsParams](30.seconds) { p =>
          p.textDocument.uri.toString.contains("Diagnostics.scala") &&
          p.diagnostics.exists(d =>
            d.severity.contains(DiagnosticSeverity.Error) &&
              (d.message.contains("type mismatch") ||
                d.message.contains("Found:") ||
                d.message.contains("Required:"))
          )
        }
        .get

      svr.session
        .waitForParamsInNotificationMsg[TaskFinishParams](30.seconds) { p =>
          p.message.contains("Compiled diagnostics")
        }
        .get

      val targets = Vector(BuildTargetIdentifier(buildTarget))
      val mainClassesId =
        sendRequest("buildTarget/scalaMainClasses", ScalaMainClassesParams(targets, None))

      svr.session
        .waitForNotificationMsg(30.seconds) { n =>
          failIfForbiddenDiagnosticReset(n)
          n.method == "build/taskFinish" &&
          n.params
            .flatMap(Converter.fromJson[TaskFinishParams](_).toOption)
            .exists(_.message.contains("Compiled diagnostics"))
        }
        .get

      svr.session.waitForResponseMsg(30.seconds, mainClassesId).get

      drainQueuedNotificationsAndFailOnForbiddenReset()
    } finally {
      IO.write(mainFile, original)
    }
  }

  test("buildTarget/compile: Java diagnostics") {
    val buildTarget = buildTargetUri("javaProj", "Compile")

    compile(buildTarget)
    svr.session
      .waitForParamsInNotificationMsg[PublishDiagnosticsParams](10.seconds) { p =>
        p.textDocument.uri.toString.contains("Hello.java") &&
        p.diagnostics.exists(d =>
          d.severity.contains(2L) &&
            d.message.contains("missing type arguments for generic class java.util.List")
        )
      }
      .get

    svr.session
      .waitForParamsInNotificationMsg[PublishDiagnosticsParams](10.seconds) { p =>
        p.textDocument.uri.toString.contains("Hello.java") &&
        p.diagnostics.exists(d =>
          d.severity.contains(1L) &&
            d.message.contains("incompatible types: int cannot be converted to java.lang.String")
        )
      }
      .get
  }

  test("buildTarget/scalacOptions, buildTarget/javacOptions") {
    val buildTargets = Seq(
      buildTargetUri("util", "Compile"),
      buildTargetUri("badBuildTarget", "Compile"),
    )

    val classDirectoryUri = new File(svr.baseDirectory, "util/classes").toURI

    val id1 = scalacOptions(buildTargets)
    val scalacResult =
      svr.session.waitForResultInResponseMsg[ScalacOptionsResult](10.seconds, id1).get
    val scalacItem = scalacResult.items.head
    assert(
      scalacItem.classpath.exists(_.toString.contains("scala-library-2.13.11.jar")),
      "scalacOptions classpath should contain scala-library"
    )
    assert(
      scalacItem.classDirectory.contains(classDirectoryUri),
      s"expected classDirectory $classDirectoryUri, got ${scalacItem.classDirectory}"
    )

    val id2 = javacOptions(buildTargets)
    val javacResult =
      svr.session.waitForResultInResponseMsg[JavacOptionsResult](10.seconds, id2).get
    val javacItem = javacResult.items.head
    assert(
      javacItem.classpath.exists(_.toString.contains("scala-library-2.13.11.jar")),
      "javacOptions classpath should contain scala-library"
    )
    assert(
      javacItem.classDirectory.contains(classDirectoryUri),
      s"expected classDirectory $classDirectoryUri, got ${javacItem.classDirectory}"
    )
  }

  test("buildTarget/cleanCache") {
    def classFile = svr.baseDirectory.toPath.resolve(
      "target/out/jvm/scala-2.13.11/runandtest/classes/main/Main.class"
    )
    val buildTarget = buildTargetUri("runAndTest", "Compile")
    val compileId = compile(buildTarget)
    svr.session.waitForResultInResponseMsg[BspCompileResult](10.seconds, compileId).get
    assert(Files.exists(classFile))

    val targets = Vector(BuildTargetIdentifier(buildTarget))
    val cleanId = sendRequest("buildTarget/cleanCache", CleanCacheParams(targets))
    val res = svr.session.waitForResultInResponseMsg[CleanCacheResult](10.seconds, cleanId).get
    assert(res.cleaned)
    assert(Files.notExists(classFile))
  }

  test("buildTarget/cleanCache: rebuild project") {
    val buildTargetsId = sendRequest("workspace/buildTargets")
    val result =
      svr.session
        .waitForResultInResponseMsg[WorkspaceBuildTargetsResult](10.seconds, buildTargetsId)
        .get
    val allTargets = result.targets.map(_.id)

    val cleanId = sendRequest("buildTarget/cleanCache", CleanCacheParams(allTargets))
    val res = svr.session.waitForResultInResponseMsg[CleanCacheResult](10.seconds, cleanId).get
    assert(res.cleaned)
  }

  test("workspace/reload") {
    val id = reloadWorkspace()
    val response = svr.session.waitForResponseMsg(10.seconds, id).get
    assert(response.result.isDefined, "workspace/reload should return a result")
  }

  test("buildTarget/scalaMainClasses") {
    val buildTarget = buildTargetUri("runAndTest", "Compile")
    val badBuildTarget = buildTargetUri("badBuildTarget", "Compile")
    val targets = Vector(buildTarget, badBuildTarget).map(BuildTargetIdentifier.apply)
    val id = sendRequest("buildTarget/scalaMainClasses", ScalaMainClassesParams(targets, None))
    val res =
      svr.session.waitForResultInResponseMsg[ScalaMainClassesResult](30.seconds, id).get
    val classes = res.items.flatMap(_.classes.map(_.`class`))
    assert(classes.contains("main.Main"))
  }

  test("buildTarget/run") {
    val buildTarget = buildTargetUri("runAndTest", "Compile")
    val mainClass = ScalaMainClass("main.Main", Vector.empty, Vector.empty, Vector.empty)
    val params = RunParams(
      BuildTargetIdentifier(buildTarget),
      None,
      Vector.empty,
      Some("scala-main-class"),
      Some(Converter.toJson(mainClass).get)
    )
    val id = sendRequest("buildTarget/run", params)
    svr.session
      .waitForParamsInNotificationMsg[LogMessageParams](10.seconds) { p =>
        p.message.contains("Hello World!")
      }
      .get
    val res = svr.session.waitForResultInResponseMsg[RunResult](10.seconds, id).get
    assert(res.statusCode == StatusCode.Success)
  }

  test("buildTarget/jvmRunEnvironment") {
    val buildTarget = buildTargetUri("runAndTest", "Compile")
    val targets = Vector(BuildTargetIdentifier(buildTarget))
    val id = sendRequest("buildTarget/jvmRunEnvironment", JvmRunEnvironmentParams(targets, None))
    val res =
      svr.session.waitForResultInResponseMsg[JvmRunEnvironmentResult](10.seconds, id).get
    val item = res.items.head
    assert(
      item.classpath.exists(_.toString.contains("jsoniter-scala-core_2.13-2.13.11.jar")),
      "classpath should contain compile dependency"
    )
    assert(item.jvmOptions.contains("Xmx256M"))
    assert(item.environmentVariables == Map("KEY" -> "VALUE"))
    assert(item.workingDirectory.contains("/buildserver/run-and-test"))
  }

  test("buildTarget/jvmTestEnvironment") {
    val buildTarget = buildTargetUri("runAndTest", "Test")
    val targets = Vector(BuildTargetIdentifier(buildTarget))
    val id = sendRequest("buildTarget/jvmTestEnvironment", JvmTestEnvironmentParams(targets, None))
    val res =
      svr.session.waitForResultInResponseMsg[JvmTestEnvironmentResult](10.seconds, id).get
    val item = res.items.head
    assert(
      item.classpath.exists(_.toString.contains("jsoniter-scala-core_2.13-2.13.11.jar")),
      "classpath should contain compile dependency"
    )
    assert(
      item.classpath.exists(_.toString.contains("scalatest_2.13-3.0.8.jar")),
      "classpath should contain test dependency"
    )
    assert(item.jvmOptions.contains("Xmx512M"))
    assert(item.environmentVariables == Map("KEY_TEST" -> "VALUE_TEST"))
  }

  test("buildTarget/scalaTestClasses") {
    val buildTarget = buildTargetUri("runAndTest", "Test")
    val badBuildTarget = buildTargetUri("badBuildTarget", "Test")
    val targets = Vector(buildTarget, badBuildTarget).map(BuildTargetIdentifier.apply)
    val id = sendRequest("buildTarget/scalaTestClasses", ScalaTestClassesParams(targets, None))
    val res =
      svr.session.waitForResultInResponseMsg[ScalaTestClassesResult](10.seconds, id).get
    val allClasses = res.items.flatMap(_.classes)
    assert(allClasses.contains("tests.FailingTest"))
    assert(allClasses.contains("tests.PassingTest"))
    val frameworks = res.items.flatMap(_.framework)
    assert(frameworks.contains("ScalaTest"))
  }

  test("buildTarget/test: run all tests") {
    val buildTarget = buildTargetUri("runAndTest", "Test")
    val targets = Vector(BuildTargetIdentifier(buildTarget))
    val id =
      sendRequest("buildTarget/test", TestParams(targets, None, Vector.empty, None, None))
    val res = svr.session.waitForResultInResponseMsg[TestResult](10.seconds, id).get
    assert(res.statusCode == StatusCode.Error)
  }

  test("buildTarget/test: run one test class") {
    val buildTarget = buildTargetUri("runAndTest", "Test")
    val target = BuildTargetIdentifier(buildTarget)
    val testClassesItem = ScalaTestClassesItem(target, Vector("tests.PassingTest"), None)
    val scalaTestParams = ScalaTestParams(Vector(testClassesItem))
    val params = TestParams(
      Vector(target),
      None,
      Vector.empty,
      Some("scala-test"),
      Some(Converter.toJson(scalaTestParams).get)
    )
    val id = sendRequest("buildTarget/test", params)
    val res = svr.session.waitForResultInResponseMsg[TestResult](10.seconds, id).get
    assert(res.statusCode == StatusCode.Success)
  }

  test("buildTarget/compile: report error") {
    val buildTarget = buildTargetUri("reportError", "Compile")
    compile(buildTarget)
    svr.session
      .waitForParamsInNotificationMsg[PublishDiagnosticsParams](10.seconds) { p =>
        p.buildTarget.uri == buildTarget &&
        p.diagnostics.exists(_.severity.contains(1L)) &&
        p.reset
      }
      .get
  }

  test("buildTarget/compile: report warning") {
    val buildTarget = buildTargetUri("reportWarning", "Compile")
    compile(buildTarget)
    svr.session
      .waitForParamsInNotificationMsg[PublishDiagnosticsParams](10.seconds) { p =>
        p.buildTarget.uri == buildTarget &&
        p.diagnostics.exists(_.severity.contains(2L)) &&
        p.reset
      }
      .get
  }

  test("buildTarget/compile: respond error") {
    val buildTarget = buildTargetUri("respondError", "Compile")
    val id = compile(buildTarget)
    val response = svr.session.waitForResponseMsg(10.seconds, id).get
    assert(response.error.exists(e => e.code == ErrorCodes.InternalError))
    assert(response.error.exists(_.message.contains("custom message")))
  }

  test("buildTarget/resources") {
    val buildTarget = buildTargetUri("util", "Compile")
    val badBuildTarget = buildTargetUri("badBuildTarget", "Compile")
    val targets = Vector(buildTarget, badBuildTarget).map(BuildTargetIdentifier.apply)
    val id = sendRequest("buildTarget/resources", ResourcesParams(targets))
    val res = svr.session.waitForResultInResponseMsg[ResourcesResult](10.seconds, id).get
    val resources = res.items.flatMap(_.resources)
    assert(
      resources.exists(_.toString.contains("util/src/main/resources/")),
      "resources should contain util/src/main/resources/"
    )
  }

  test("buildTarget/dependencyModules") {
    val buildTarget = buildTargetUri("runAndTest", "Compile")
    val badBuildTarget = buildTargetUri("badBuildTarget", "Compile")
    val targets = Vector(buildTarget, badBuildTarget).map(BuildTargetIdentifier.apply)
    val id = dependencyModules(targets.map(_.uri))
    val res = svr.session.waitForResultInResponseMsg[DependencyModulesResult](10.seconds, id).get
    val runAndTestItem = res.items.find(_.target.contains(BuildTargetIdentifier(buildTarget))).get
    assert(
      runAndTestItem.modules.exists(_.name.contains("jsoniter-scala-core")),
      s"dependencyModules should include jsoniter-scala-core, got: ${runAndTestItem.modules.map(_.name)}"
    )
  }

  test("buildTarget/outputPaths") {
    val buildTarget = buildTargetUri("util", "Compile")
    val badBuildTarget = buildTargetUri("badBuildTarget", "Compile")
    val targets = Vector(buildTarget, badBuildTarget).map(BuildTargetIdentifier.apply)
    val id = sendRequest("buildTarget/outputPaths", OutputPathsParams(targets))
    val actualResult =
      svr.session.waitForResultInResponseMsg[OutputPathsResult](10.seconds, id).get
    val expectedResult = OutputPathsResult(
      items = Vector(
        OutputPathsItem(
          target = BuildTargetIdentifier(buildTarget),
          outputPaths = Vector(
            OutputPathItem(
              uri = new File(svr.baseDirectory, "target/out/jvm/scala-2.13.11/util/").toURI,
              kind = OutputPathItemKind.Directory
            )
          )
        )
      )
    )
    assert(actualResult == expectedResult)
  }

  ignore("buildTarget/compile: twirl diagnostics (sourcePositionMappers)") {
    val buildTarget = buildTargetUri("twirlProj", "Compile")
    val testFile = new File(svr.baseDirectory, s"twirlProj/src/main/twirl/main.scala.html")

    compile(buildTarget)
    svr.session
      .waitForParamsInNotificationMsg[PublishDiagnosticsParams](10.seconds) { p =>
        p.textDocument.uri.toString.contains("main.scala.html") &&
        p.diagnostics
          .exists(d => d.severity.contains(1L) && d.message.contains("not found: value title_"))
      }
      .get
    IO.write(
      testFile,
      """|@(title: String, paragraphs: Seq[String])
         |
         |<!DOCTYPE HTML>
         |<html lang="en">
         |  <head>
         |    <title>@title</title>
         |  </head>
         |  <body>
         |    <h1>@title</h1>
         |    @for(paragraph <- paragraphs) {
         |      <p>@paragraph</p>
         |    }
         |  </body>
         |</html>
         |""".stripMargin
    )
    compile(buildTarget)
    svr.session
      .waitForParamsInNotificationMsg[PublishDiagnosticsParams](30.seconds) { p =>
        p.textDocument.uri.toString.contains("main.scala.html") &&
        p.diagnostics.isEmpty &&
        p.reset
      }
      .get
  }

  /*
  test("buildTarget/compile [Java diagnostics] clear stale warnings") {
    val buildTarget = buildTargetUri("javaProj", "Compile")
    val testFile = new File(svr.baseDirectory, s"java-proj/src/main/java/example/Hello.java")

    val otherBuildFile = new File(svr.baseDirectory, "force-java-out-of-process-compiler.sbt")
    // Setting `javaHome` will force sbt to shell out to an external Java compiler instead
    // of using the local compilation service offered by the JVM running this SBT instance.
    IO.write(
      otherBuildFile,
      """
        |def jdk: File = sbt.internal.util.Util.javaHome.toFile()
        |lazy val javaProj = project
        |  .in(file("java-proj"))
        |  .settings(
        |    javacOptions += "-Xlint:all",
        |    javaHome := Some(jdk)
        |  )
        |""".stripMargin
    )
    reloadWorkspace()

    compile(buildTarget)

    assertMessage(
      "build/publishDiagnostics",
      "Hello.java",
      """"severity":2""",
      """found raw type"""
    )(message = "should send publishDiagnostics with severity 2 for Hello.java", debug = false)

    assertMessage(
      "build/publishDiagnostics",
      "Hello.java",
      """"severity":1""",
      """incompatible types: int cannot be converted"""
    )(
      message = "should send publishDiagnostics with severity 1 for Hello.java"
    )
    // Note the messages changed slightly in both cases. That's interesting…

    IO.write(
      testFile,
      """|package example;
         |
         |import java.util.List;
         |import java.util.ArrayList;
         |
         |class Hello {
         |    public static void main(String[] args) {
         |        List<String> list = new ArrayList<>();
         |        String msg = "42";
         |        System.out.println(msg);
         |    }
         |}
         |""".stripMargin
    )

    compile(buildTarget)
    /*
    assertMessage(
      "build/publishDiagnostics",
      "Hello.java",
      "\"diagnostics\":[]",
      "\"reset\":true"
    )(
      message = "should send publishDiagnostics with empty diagnostics",
      debug = false
    )
   */

    IO.delete(otherBuildFile)
    reloadWorkspace()
    ()
  }*/

  /*
  test("workspace/reload: send diagnostic and respond with error") {
    // write an other-build.sbt file that does not compile
    val otherBuildFile = svr.baseDirectory.toPath.resolve("other-build.sbt")
    Files.write(
      otherBuildFile,
      """|val someSettings = Seq(
         |  scalacOptions ++= "-deprecation"
         |)
         |""".stripMargin.getBytes
    )
    val id = reloadWorkspace()
    // reload
    assertMessage(
      s""""buildTarget":{"uri":"$metaBuildTarget"}""",
      s""""textDocument":{"uri":"${otherBuildFile.toUri}"}""",
      """"severity":1""",
      """"reset":true"""
    )()

    assertMessage(
      s""""id":"$id"""",
      """"error"""",
      s""""code":${ErrorCodes.InternalError}""",
      "String cannot be appended to Seq[String]"
    )()
    // fix the other-build.sbt file and reload again
    Files.write(
      otherBuildFile,
      """|val someSettings = Seq(
         |  scalacOptions += "-deprecation"
         |)
         |""".stripMargin.getBytes
    )
    reloadWorkspace()
    // assert received an empty diagnostic
    assertMessage(
      s""""buildTarget":{"uri":"$metaBuildTarget"}""",
      s""""textDocument":{"uri":"${otherBuildFile.toUri}"}""",
      """"diagnostics":[]""",
      """"reset":true"""
    )()
  }
   */

  private def initializeRequest(): String = {
    val params = InitializeBuildParams(
      "test client",
      "1.0.0",
      "2.1.0-M1",
      new URI("file://root/"),
      BuildClientCapabilities(Vector("scala")),
      None
    )
    sendRequest("build/initialize", params)
  }

  private def assertProcessing(method: String): Unit =
    svr.session
      .waitForParamsInNotificationMsg[LogMessageParams](10.seconds) { p =>
        p.message.contains(s"Processing $method")
      }
      .get

  private def reloadWorkspace(): String =
    sendRequest("workspace/reload")

  private def compile(buildTarget: URI): String = {
    val params =
      CompileParams(targets = Vector(BuildTargetIdentifier(buildTarget)), None, Vector.empty)
    sendRequest("buildTarget/compile", params)
  }

  private def scalacOptions(buildTargets: Seq[URI]): String = {
    val targets = buildTargets.map(BuildTargetIdentifier.apply).toVector
    sendRequest("buildTarget/scalacOptions", ScalacOptionsParams(targets))
  }

  // sbt serves javac options via buildTarget/scalacOptions (no separate javacOptions endpoint)
  private def javacOptions(buildTargets: Seq[URI]): String = {
    val targets = buildTargets.map(BuildTargetIdentifier.apply).toVector
    sendRequest("buildTarget/scalacOptions", ScalacOptionsParams(targets))
  }

  private def buildTargetSources(buildTargets: Seq[URI]): String = {
    val targets = buildTargets.map(BuildTargetIdentifier.apply).toVector
    sendRequest("buildTarget/sources", SourcesParams(targets))
  }

  private def dependencyModules(buildTargets: Seq[URI]): String = {
    val targets = buildTargets.map(BuildTargetIdentifier.apply).toVector
    sendRequest("buildTarget/dependencyModules", DependencyModulesParams(targets))
  }

  private def sendRequest(method: String): String = {
    val id = svr.session.nextId()
    svr.session.sendJsonRpc(id, method, "{}").get
    if (method != "build/initialize") assertProcessing(method)
    id
  }

  private def sendRequest[T: JsonWriter](method: String, params: T): String = {
    val id = svr.session.nextId()
    svr.session.sendJsonRpc(id, method, params).get
    if (method != "build/initialize") assertProcessing(method)
    id
  }

  private def buildTargetUri(project: String, config: String): URI =
    new URI(s"${svr.baseDirectory.getAbsoluteFile.toURI}#$project/$config")
}
