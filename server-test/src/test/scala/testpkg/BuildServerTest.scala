/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package testpkg

import sbt.internal.bsp._
import sbt.internal.langserver.ErrorCodes
import sbt.IO
import sbt.internal.protocol.JsonRpcRequestMessage
import sbt.internal.protocol.codec.JsonRPCProtocol._
import sjsonnew.JsonWriter
import sjsonnew.support.scalajson.unsafe.{ CompactPrinter, Converter }

import java.io.File
import java.net.URI
import java.nio.file.Paths
import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.duration._

// starts svr using server-test/buildserver and perform custom server tests
object BuildServerTest extends AbstractServerTest {

  import sbt.internal.bsp.codec.JsonProtocol._

  override val testDirectory: String = "buildserver"

  private val idGen: AtomicInteger = new AtomicInteger(0)
  private def nextId(): Int = idGen.getAndIncrement()

  test("build/initialize") { _ =>
    val id = initializeRequest()
    assertMessage(
      s""""id":"${id}"""",
      """"resourcesProvider":true""",
      """"outputPathsProvider":true"""
    )()
  }

  test("workspace/buildTargets") { _ =>
    sendRequest("workspace/buildTargets")
    val result = svr.waitFor[WorkspaceBuildTargetsResult](10.seconds)
    val utilTargetIdentifier = BuildTargetIdentifier(buildTargetUri("util", "Compile"))
    val utilTarget = result.targets.find(_.id == utilTargetIdentifier).get
    assert(utilTarget.id.uri.toString.endsWith("#util/Compile"))
    val runAndTestTarget = result.targets.find(_.displayName.contains("runAndTest")).get
    // runAndTest should declare the dependency to util even if optional
    assert(runAndTestTarget.dependencies.contains(utilTargetIdentifier))
    val buildServerBuildTarget =
      result.targets.find(_.displayName.contains("buildserver-build")).get
    assert(buildServerBuildTarget.id.uri.toString.endsWith("#buildserver-build"))
    assert(!result.targets.exists(_.displayName.contains("badBuildTarget")))
  }

  test("buildTarget/sources") { _ =>
    val buildTarget = buildTargetUri("util", "Compile")
    val badBuildTarget = buildTargetUri("badBuildTarget", "Compile")
    buildTargetSources(Seq(buildTarget, badBuildTarget))
    val s = svr.waitFor[SourcesResult](10.seconds)
    val sources = s.items.head.sources.map(_.uri)
    assert(sources.contains(new File(svr.baseDirectory, "util/src/main/scala").toURI))
  }
  test("buildTarget/sources: base sources") { _ =>
    val buildTarget = buildTargetUri("buildserver", "Compile")
    buildTargetSources(Seq(buildTarget))
    val s = svr.waitFor[SourcesResult](10.seconds)
    val sources = s.items.head.sources
    val expectedSource = SourceItem(
      new File(svr.baseDirectory, "BaseSource.scala").toURI,
      SourceItemKind.File,
      generated = false
    )
    assert(sources.contains(expectedSource))
  }

  test("buildTarget/sources: sbt") { _ =>
    val x = new URI(s"${svr.baseDirectory.getAbsoluteFile.toURI}#buildserver-build")
    buildTargetSources(Seq(x))
    val s = svr.waitFor[SourcesResult](10.seconds)
    val sources = s.items.head.sources.map(_.uri).sorted
    val expectedSources = Vector(
      "build.sbt",
      "project/A.scala",
      "project/src/main/java",
      "project/src/main/scala-2",
      "project/src/main/scala-2.12",
      "project/src/main/scala-sbt-1.0",
      "project/src/main/scala/",
      "project/target/scala-2.12/sbt-1.0/src_managed/main"
    ).map(rel => new File(svr.baseDirectory.getAbsoluteFile, rel).toURI).sorted
    assert(sources == expectedSources)
  }

  test("buildTarget/compile") { _ =>
    val buildTarget = buildTargetUri("util", "Compile")
    compile(buildTarget)
    val res = svr.waitFor[BspCompileResult](10.seconds)
    assert(res.statusCode == StatusCode.Success)
  }

  test("buildTarget/compile - reports compilation progress") { _ =>
    val buildTarget = buildTargetUri("runAndTest", "Compile")
    compile(buildTarget)

    // This doesn't always come back in 10s on CI.
    assertMessage("build/taskStart", """"message":"Compiling runAndTest"""")(duration = 60.seconds)
    assertMessage(
      "build/taskProgress",
      """"message":"Compiling runAndTest (15%)""""
    )(duration = 60.seconds)
    assertMessage(
      "build/taskProgress",
      """"message":"Compiling runAndTest (100%)""""
    )(duration = 60.seconds)
    assertMessage("build/publishDiagnostics", """"diagnostics":[]""")(duration = 60.seconds)
    assertMessage("build/taskFinish", """"message":"Compiled runAndTest"""")(duration = 60.seconds)
  }

  test(
    "buildTarget/compile [diagnostics] don't publish unnecessary for successful compilation case"
  ) { _ =>
    val buildTarget = buildTargetUri("diagnostics", "Compile")
    val mainFile = new File(svr.baseDirectory, "diagnostics/src/main/scala/Diagnostics.scala")

    compile(buildTarget)

    assertMessage("build/taskFinish", """"message":"Compiled diagnostics"""")(30.seconds)

    // introduce compile error
    IO.write(
      mainFile,
      """|object Diagnostics {
         |  private val a: Int = ""
         |}""".stripMargin
    )

    compile(buildTarget)

    assertMessage(
      "build/publishDiagnostics",
      "Diagnostics.scala",
      "\"message\":\"type mismatch"
    )(
      duration = 30.seconds,
      message = "should send publishDiagnostics with type error for Main.scala"
    )

    // fix compilation error
    IO.write(
      mainFile,
      """|object Diagnostics {
         |  private val a: Int = 5
         |}""".stripMargin
    )

    reloadWorkspace()
    compile(buildTarget)

    assertMessage("build/publishDiagnostics", "Diagnostics.scala", "\"diagnostics\":[]")(
      duration = 30.seconds,
      message = "should send publishDiagnostics with empty diagnostics"
    )
    assertMessage("build/taskFinish", "\"noOp\":true")(debug = true)

    // trigger no-op compilation
    compile(buildTarget)

    assert(
      svr.waitForString(20.seconds) { s =>
        if (s.contains("build/publishDiagnostics") && s.contains("Diagnostics.scala"))
          throw new Exception("shouldn't send publishDiagnostics if noOp compilation")
        else s.contains("build/taskFinish") && s.contains("\"noOp\":true")
      },
      "shouldn't send publishDiagnostics if there's no change in diagnostics (were empty, are empty)"
    )
  }

  test("buildTarget/compile [diagnostics] clear stale warnings") { _ =>
    val buildTarget = buildTargetUri("diagnostics", "Compile")
    val testFile = new File(svr.baseDirectory, s"diagnostics/src/main/scala/PatternMatch.scala")

    compile(buildTarget)

    assertMessage(
      "build/publishDiagnostics",
      "PatternMatch.scala",
      """"message":"match may not be exhaustive"""
    )(
      duration = 30.seconds,
      message = "should send publishDiagnostics with type error for PatternMatch.scala"
    )

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

    assertMessage("build/publishDiagnostics", "PatternMatch.scala", "\"diagnostics\":[]")(
      duration = 30.seconds,
      message = "should send publishDiagnostics with empty diagnostics"
    )
  }

  test("buildTarget/compile: Java diagnostics") { _ =>
    val buildTarget = buildTargetUri("javaProj", "Compile")

    compile(buildTarget)

    assertMessage(
      "build/publishDiagnostics",
      "Hello.java",
      """"severity":2""",
      """missing type arguments for generic class java.util.List"""
    )(message = "should send publishDiagnostics with severity 2 for Hello.java")

    assertMessage(
      "build/publishDiagnostics",
      "Hello.java",
      """"severity":1""",
      """incompatible types: int cannot be converted to java.lang.String"""
    )(
      message = "should send publishDiagnostics with severity 1 for Hello.java"
    )
  }

  test("buildTarget/scalacOptions, buildTarget/javacOptions") { _ =>
    val buildTargets = Seq(
      buildTargetUri("util", "Compile"),
      buildTargetUri("badBuildTarget", "Compile"),
    )

    val id1 = scalacOptions(buildTargets)
    assertMessage(s""""id":"$id1"""", "scala-library-2.13.11.jar")()

    val id2 = javacOptions(buildTargets)
    assertMessage(s""""id":"$id2"""", "scala-library-2.13.11.jar")()

    val id3 = scalacOptions(Seq(buildTargetUri("runAndTest", "Compile")))
    assertMessage(s""""id":"$id3"""", "util/target/scala-2.13/classes")()
  }

  test("buildTarget/cleanCache") { _ =>
    def targetDir =
      Paths
        .get(
          svr.baseDirectory.getAbsoluteFile.toString,
          "run-and-test/target/scala-2.13/classes/main"
        )
        .toFile

    val buildTarget = buildTargetUri("runAndTest", "Compile")
    compile(buildTarget)
    svr.waitFor[BspCompileResult](10.seconds)
    assert(targetDir.list().contains("Main.class"))

    svr.sendJsonRpc(
      s"""{ "jsonrpc": "2.0", "id": "${nextId()}", "method": "buildTarget/cleanCache", "params": {
         |  "targets": [{ "uri": "$buildTarget" }]
         |} }""".stripMargin
    )
    assertProcessing("buildTarget/cleanCache")
    val res = svr.waitFor[CleanCacheResult](10.seconds)
    assert(res.cleaned)
    assert(targetDir.list().isEmpty)
  }

  test("buildTarget/cleanCache: rebuild project") { _ =>
    svr.sendJsonRpc(
      s"""{ "jsonrpc": "2.0", "id": "${nextId()}", "method": "workspace/buildTargets", "params": {} }"""
    )
    assertProcessing("workspace/buildTargets")
    val result = svr.waitFor[WorkspaceBuildTargetsResult](10.seconds)
    val allTargets = result.targets.map(_.id.uri)

    svr.sendJsonRpc(
      s"""{ "jsonrpc": "2.0", "id": "${nextId()}", "method": "buildTarget/cleanCache", "params": {
         |  "targets": [
         |    ${allTargets.map(uri => s"""{ "uri": "$uri" }""").mkString(",\n")}
         |  ]
         |} }""".stripMargin
    )
    assertProcessing("buildTarget/cleanCache")
    val res = svr.waitFor[CleanCacheResult](10.seconds)
    assert(res.cleaned)
  }

  test("workspace/reload") { _ =>
    val id = nextId()
    svr.sendJsonRpc(
      s"""{ "jsonrpc": "2.0", "id": "$id", "method": "workspace/reload"}"""
    )
    assertProcessing("workspace/reload")
    assertMessage(s""""id":"$id"""", """"result":null""")()
  }

  test("workspace/reload: send diagnostic and respond with error") { _ =>
    // write an other-build.sbt file that does not compile
    val otherBuildFile = new File(svr.baseDirectory, "other-build.sbt")
    IO.write(
      otherBuildFile,
      """
        |val someSettings = Seq(
        |  scalacOptions ++= "-deprecation"
        |)
        |""".stripMargin
    )
    val id = reloadWorkspace()
    // reload
    assertMessage(
      s""""buildTarget":{"uri":"$metaBuildTarget"}""",
      s""""textDocument":{"uri":"${otherBuildFile.toPath.toUri}"}""",
      """"severity":1""",
      """"reset":true"""
    )()

    assertMessage(
      s""""id":"$id"""",
      """"error"""",
      s""""code":${ErrorCodes.InternalError}""",
      "Type error in expression"
    )()
    // fix the other-build.sbt file and reload again
    IO.write(
      otherBuildFile,
      """
        |val someSettings = Seq(
        |  scalacOptions += "-deprecation"
        |)
        |""".stripMargin
    )
    reloadWorkspace()
    // assert received an empty diagnostic
    assertMessage(
      s""""buildTarget":{"uri":"$metaBuildTarget"}""",
      s""""textDocument":{"uri":"${otherBuildFile.toPath.toUri}"}""",
      """"diagnostics":[]""",
      """"reset":true"""
    )()
    IO.delete(otherBuildFile)
  }

  test("buildTarget/scalaMainClasses") { _ =>
    val buildTarget = buildTargetUri("runAndTest", "Compile")
    val badBuildTarget = buildTargetUri("badBuildTarget", "Compile")
    val id = nextId()
    svr.sendJsonRpc(
      s"""{ "jsonrpc": "2.0", "id": "$id", "method": "buildTarget/scalaMainClasses", "params": {
         |  "targets": [{ "uri": "$buildTarget" }, { "uri": "$badBuildTarget" }]
         |} }""".stripMargin
    )
    assertProcessing("buildTarget/scalaMainClasses")
    assertMessage(s""""id":"$id"""", """"class":"main.Main"""")(duration = 30.seconds)
  }

  test("buildTarget/run") { _ =>
    val buildTarget = buildTargetUri("runAndTest", "Compile")
    val id = nextId()
    svr.sendJsonRpc(
      s"""{ "jsonrpc": "2.0", "id": "$id", "method": "buildTarget/run", "params": {
         |  "target": { "uri": "$buildTarget" },
         |  "dataKind": "scala-main-class",
         |  "data": { "class": "main.Main" }
         |} }""".stripMargin
    )
    assertProcessing("buildTarget/run")
    assertMessage("build/logMessage", """"message":"Hello World!"""")()
    assertMessage(s""""id":"$id"""", """"statusCode":1""")()
  }

  test("buildTarget/jvmRunEnvironment") { _ =>
    val buildTarget = buildTargetUri("runAndTest", "Compile")
    val id = nextId()
    svr.sendJsonRpc(
      s"""|{ "jsonrpc": "2.0",
          |  "id": "$id",
          |  "method": "buildTarget/jvmRunEnvironment",
          |  "params": { "targets": [{ "uri": "$buildTarget" }] }
          |}""".stripMargin
    )
    assertProcessing("buildTarget/jvmRunEnvironment")
    assertMessage(
      s""""id":"$id"""",
      "jsoniter-scala-core_2.13-2.13.11.jar", // compile dependency
      "\"jvmOptions\":[\"Xmx256M\"]",
      "\"environmentVariables\":{\"KEY\":\"VALUE\"}",
      "/buildserver/run-and-test/" // working directory
    )()
  }

  test("buildTarget/jvmTestEnvironment") { _ =>
    val buildTarget = buildTargetUri("runAndTest", "Test")
    val id = nextId()
    svr.sendJsonRpc(
      s"""|{ "jsonrpc": "2.0",
          |  "id": "$id",
          |  "method": "buildTarget/jvmTestEnvironment",
          |  "params": { "targets": [{ "uri": "$buildTarget" }] }
          |}""".stripMargin
    )
    assertProcessing("buildTarget/jvmTestEnvironment")
    assertMessage(
      s""""id":"$id"""",
      "jsoniter-scala-core_2.13-2.13.11.jar", // compile dependency
      "scalatest_2.13-3.0.8.jar", // test dependency
      "\"jvmOptions\":[\"Xmx512M\"]",
      "\"environmentVariables\":{\"KEY_TEST\":\"VALUE_TEST\"}"
    )()
  }

  test("buildTarget/scalaTestClasses") { _ =>
    val buildTarget = buildTargetUri("runAndTest", "Test")
    val badBuildTarget = buildTargetUri("badBuildTarget", "Test")
    val id = nextId()
    svr.sendJsonRpc(
      s"""{ "jsonrpc": "2.0", "id": "$id", "method": "buildTarget/scalaTestClasses", "params": {
         |  "targets": [{ "uri": "$buildTarget" }, { "uri": "$badBuildTarget" }]
         |} }""".stripMargin
    )
    assertProcessing("buildTarget/scalaTestClasses")
    assertMessage(
      s""""id":"$id"""",
      """"tests.FailingTest"""",
      """"tests.PassingTest"""",
      """"framework":"ScalaTest""""
    )()
  }

  test("buildTarget/test: run all tests") { _ =>
    val buildTarget = buildTargetUri("runAndTest", "Test")
    val id = nextId()
    svr.sendJsonRpc(
      s"""{ "jsonrpc": "2.0", "id": "$id", "method": "buildTarget/test", "params": {
         |  "targets": [{ "uri": "$buildTarget" }]
         |} }""".stripMargin
    )
    assertProcessing("buildTarget/test")
    assertMessage(s""""id":"$id"""", """"statusCode":2""")()
  }

  test("buildTarget/test: run one test class") { _ =>
    val buildTarget = buildTargetUri("runAndTest", "Test")
    val id = nextId()
    svr.sendJsonRpc(
      s"""{ "jsonrpc": "2.0", "id": "$id", "method": "buildTarget/test", "params": {
         |  "targets": [{ "uri": "$buildTarget" }],
         |  "dataKind": "scala-test",
         |  "data": {
         |    "testClasses": [
         |      {
         |        "target": { "uri": "$buildTarget" },
         |        "classes": ["tests.PassingTest"]
         |      }
         |    ]
         |  }
         |} }""".stripMargin
    )
    assertProcessing("buildTarget/test")
    assertMessage(s""""id":"$id"""", """"statusCode":1""")()
  }

  test("buildTarget/compile: report error") { _ =>
    val buildTarget = buildTargetUri("reportError", "Compile")
    compile(buildTarget)
    assertMessage(
      s""""buildTarget":{"uri":"$buildTarget"}""",
      """"severity":1""",
      """"reset":true"""
    )()
  }

  test("buildTarget/compile: report warning") { _ =>
    val buildTarget = buildTargetUri("reportWarning", "Compile")
    compile(buildTarget)
    assertMessage(
      s""""buildTarget":{"uri":"$buildTarget"}""",
      """"severity":2""",
      """"reset":true"""
    )()
  }

  test("buildTarget/compile: respond error") { _ =>
    val buildTarget = buildTargetUri("respondError", "Compile")
    val id = compile(buildTarget)
    assertMessage(
      s""""id":"$id"""",
      """"error"""",
      s""""code":${ErrorCodes.InternalError}""",
      "custom message"
    )()
  }

  test("buildTarget/resources") { _ =>
    val buildTarget = buildTargetUri("util", "Compile")
    val badBuildTarget = buildTargetUri("badBuildTarget", "Compile")
    val id = nextId()
    svr.sendJsonRpc(
      s"""{ "jsonrpc": "2.0", "id": "$id", "method": "buildTarget/resources", "params": {
         |  "targets": [{ "uri": "$buildTarget" }, { "uri": "$badBuildTarget" }]
         |} }""".stripMargin
    )
    assertProcessing("buildTarget/resources")
    assertMessage(s""""id":"$id"""", "util/src/main/resources/")()
  }

  test("buildTarget/outputPaths") { _ =>
    val buildTarget = buildTargetUri("util", "Compile")
    val badBuildTarget = buildTargetUri("badBuildTarget", "Compile")
    svr.sendJsonRpc(
      s"""{ "jsonrpc": "2.0", "id": "${nextId()}", "method": "buildTarget/outputPaths", "params": {
         |  "targets": [{ "uri": "$buildTarget" }, { "uri": "$badBuildTarget" }]
         |} }""".stripMargin
    )
    assertProcessing("buildTarget/outputPaths")
    val actualResult = svr.waitFor[OutputPathsResult](10.seconds)
    val expectedResult = OutputPathsResult(
      items = Vector(
        OutputPathsItem(
          target = BuildTargetIdentifier(buildTarget),
          outputPaths = Vector(
            OutputPathItem(
              uri = new File(svr.baseDirectory, "util/custom-target").toURI,
              kind = OutputPathItemKind.Directory
            )
          )
        )
      )
    )
    assert(actualResult == expectedResult)
  }

  test("buildTarget/compile: twirl diagnostics (sourcePositionMappers)") { _ =>
    val buildTarget = buildTargetUri("twirlProj", "Compile")
    val testFile = new File(svr.baseDirectory, s"twirlProj/src/main/twirl/main.scala.html")

    compile(buildTarget)
    assertMessage(
      "build/publishDiagnostics",
      "main.scala.html",
      """"severity":1""",
      "not found: value tilte"
    )(message = "should report diagnostic in Twirl file")
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
    assertMessage(
      "build/publishDiagnostics",
      "main.scala.html",
      """"diagnostics":[]""",
      """"reset":true"""
    )(
      duration = 30.seconds,
      message = "should reset diagnostic in Twirl file"
    )
  }

  private def initializeRequest(): Int = {
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

  private def assertProcessing(method: String, debug: Boolean = false): Unit =
    assertMessage("build/logMessage", s""""message":"Processing $method"""")(debug = debug)

  def assertMessage(
      parts: String*
  )(duration: FiniteDuration = 10.seconds, debug: Boolean = false, message: String = ""): Unit = {
    def assertion =
      svr.waitForString(duration) { msg =>
        if (debug) println(msg)
        parts.forall(msg.contains)
      }
    if (message.nonEmpty) assert.apply(assertion, message) else assert(assertion)
  }

  private def reloadWorkspace(): Int =
    sendRequest("workspace/reload")

  private def compile(buildTarget: URI): Int = {
    val params =
      CompileParams(targets = Vector(BuildTargetIdentifier(buildTarget)), None, Vector.empty)
    sendRequest("buildTarget/compile", params)
  }

  private def scalacOptions(buildTargets: Seq[URI]): Int = {
    val targets = buildTargets.map(BuildTargetIdentifier.apply).toVector
    sendRequest("buildTarget/scalacOptions", ScalacOptionsParams(targets))
  }

  private def javacOptions(buildTargets: Seq[URI]): Int = {
    val targets = buildTargets.map(BuildTargetIdentifier.apply).toVector
    sendRequest("buildTarget/scalacOptions", ScalacOptionsParams(targets))
  }

  private def buildTargetSources(buildTargets: Seq[URI]): Int = {
    val targets = buildTargets.map(BuildTargetIdentifier.apply).toVector
    sendRequest("buildTarget/sources", SourcesParams(targets))
  }

  private def sendRequest(method: String): Int = {
    val id = nextId()
    val msg = JsonRpcRequestMessage("2.0", id.toString, method, None)
    val json = Converter.toJson(msg).get
    svr.sendJsonRpc(CompactPrinter(json))
    id
  }

  private def sendRequest[T: JsonWriter](method: String, params: T): Int = {
    val id = nextId()
    val msg = JsonRpcRequestMessage("2.0", id.toString, method, Converter.toJson(params).get)
    val json = Converter.toJson(msg).get
    svr.sendJsonRpc(CompactPrinter(json))
    if (method != "build/initialize") assertProcessing(method)
    id
  }

  private def buildTargetUri(project: String, config: String): URI =
    new URI(s"${svr.baseDirectory.getAbsoluteFile.toURI}#$project/$config")

  private def metaBuildTarget: String =
    s"${svr.baseDirectory.getAbsoluteFile.toURI}project/#buildserver-build/Compile"
}
