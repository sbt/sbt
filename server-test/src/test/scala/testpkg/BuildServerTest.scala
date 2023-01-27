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
    val id = nextId()
    initializeRequest(id)
    assert(svr.waitForString(10.seconds) { s =>
      (s contains s""""id":"${id}"""") &&
      (s contains """"resourcesProvider":true""") &&
      (s contains """"outputPathsProvider":true""")
    })
  }

  test("workspace/buildTargets") { _ =>
    svr.sendJsonRpc(
      s"""{ "jsonrpc": "2.0", "id": "${nextId()}", "method": "workspace/buildTargets", "params": {} }"""
    )
    assert(processing("workspace/buildTargets"))
    val result = svr.waitFor[WorkspaceBuildTargetsResult](10.seconds)
    val utilTarget = result.targets.find(_.displayName.contains("util")).get
    assert(utilTarget.id.uri.toString.endsWith("#util/Compile"))
    val buildServerBuildTarget =
      result.targets.find(_.displayName.contains("buildserver-build")).get
    assert(buildServerBuildTarget.id.uri.toString.endsWith("#buildserver-build"))
    assert(!result.targets.exists(_.displayName.contains("badBuildTarget")))
  }

  test("buildTarget/sources") { _ =>
    val buildTarget = buildTargetUri("util", "Compile")
    val badBuildTarget = buildTargetUri("badBuildTarget", "Compile")
    svr.sendJsonRpc(buildTargetSources(Seq(buildTarget, badBuildTarget)))
    assert(processing("buildTarget/sources"))
    val s = svr.waitFor[SourcesResult](10.seconds)
    val sources = s.items.head.sources.map(_.uri)
    assert(sources.contains(new File(svr.baseDirectory, "util/src/main/scala").toURI))
  }
  test("buildTarget/sources: base sources") { _ =>
    val buildTarget = buildTargetUri("buildserver", "Compile")
    svr.sendJsonRpc(buildTargetSources(Seq(buildTarget)))
    assert(processing("buildTarget/sources"))
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
    svr.sendJsonRpc(buildTargetSources(Seq(x)))
    assert(processing("buildTarget/sources"))
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

    compile(buildTarget, id = nextId())

    assert(processing("buildTarget/compile"))
    val res = svr.waitFor[BspCompileResult](10.seconds)
    assert(res.statusCode == StatusCode.Success)
  }

  test("buildTarget/compile - reports compilation progress") { _ =>
    val buildTarget = buildTargetUri("runAndTest", "Compile")

    compile(buildTarget)

    // This doesn't always come back in 10s on CI.
    assert(svr.waitForString(60.seconds) { s =>
      s.contains("build/taskStart") &&
      s.contains(""""message":"Compiling runAndTest"""")
    })

    assert(svr.waitForString(60.seconds) { s =>
      s.contains("build/taskProgress") &&
      s.contains(""""message":"Compiling runAndTest (15%)"""")
    })

    assert(svr.waitForString(60.seconds) { s =>
      s.contains("build/taskProgress") &&
      s.contains(""""message":"Compiling runAndTest (100%)"""")
    })

    assert(svr.waitForString(60.seconds) { s =>
      s.contains("build/publishDiagnostics") &&
      s.contains(""""diagnostics":[]""")
    })

    assert(svr.waitForString(60.seconds) { s =>
      s.contains("build/taskFinish") &&
      s.contains(""""message":"Compiled runAndTest"""")
    })
  }

  test(
    "buildTarget/compile [diagnostics] don't publish unnecessary for successful compilation case"
  ) { _ =>
    val buildTarget = buildTargetUri("diagnostics", "Compile")
    val mainFile = new File(svr.baseDirectory, "diagnostics/src/main/scala/Diagnostics.scala")

    compile(buildTarget)

    assert(svr.waitForString(30.seconds) { s =>
      s.contains("build/taskFinish") &&
      s.contains(""""message":"Compiled diagnostics"""")
    })

    // introduce compile error
    IO.write(
      mainFile,
      """|object Diagnostics {
         |  private val a: Int = ""
         |}""".stripMargin
    )

    reloadWorkspace()
    compile(buildTarget)

    assert(
      svr.waitForString(30.seconds) { s =>
        s.contains("build/publishDiagnostics") &&
        s.contains("Diagnostics.scala") &&
        s.contains("\"message\":\"type mismatch")
      },
      "should send publishDiagnostics with type error for Main.scala"
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

    assert(
      svr.waitForString(30.seconds) { s =>
        s.contains("build/publishDiagnostics") &&
        s.contains("Diagnostics.scala") &&
        s.contains("\"diagnostics\":[]")
      },
      "should send publishDiagnostics with empty diagnostics"
    )

    // trigger no-op compilation
    compile(buildTarget)

    assert(
      !svr.waitForString(20.seconds) { s =>
        s.contains("build/publishDiagnostics") &&
        s.contains("Diagnostics.scala")
      },
      "shouldn't send publishDiagnostics if there's no change in diagnostics (were empty, are empty)"
    )
  }

  test("buildTarget/compile [diagnostics] clear stale warnings") { _ =>
    val buildTarget = buildTargetUri("diagnostics", "Compile")
    val testFile = new File(svr.baseDirectory, s"diagnostics/src/main/scala/PatternMatch.scala")

    compile(buildTarget)

    assert(
      svr.waitForString(30.seconds) { s =>
        s.contains("build/publishDiagnostics") &&
        s.contains("PatternMatch.scala") &&
        s.contains(""""message":"match may not be exhaustive""")
      },
      "should send publishDiagnostics with type error for PatternMatch.scala"
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

    assert(
      svr.waitForString(30.seconds) { s =>
        s.contains("build/publishDiagnostics") &&
        s.contains("PatternMatch.scala") &&
        s.contains("\"diagnostics\":[]")
      },
      "should send publishDiagnostics with empty diagnostics"
    )

  }

  test("buildTarget/compile: Java diagnostics") { _ =>
    val buildTarget = buildTargetUri("javaProj", "Compile")

    compile(buildTarget)

    assert(
      svr.waitForString(10.seconds) { s =>
        s.contains("build/publishDiagnostics") &&
        s.contains("Hello.java") &&
        s.contains(""""severity":2""") &&
        s.contains("""missing type arguments for generic class java.util.List""")
      },
      "should send publishDiagnostics with serverity 2 for Hello.java"
    )

    assert(
      svr.waitForString(1.seconds) { s =>
        s.contains("build/publishDiagnostics") &&
        s.contains("Hello.java") &&
        s.contains(""""severity":1""") &&
        s.contains("""incompatible types: int cannot be converted to java.lang.String""")
      },
      "should send publishDiagnostics with serverity 1 for Hello.java"
    )
  }

  test("buildTarget/scalacOptions") { _ =>
    val buildTarget = buildTargetUri("util", "Compile")
    val badBuildTarget = buildTargetUri("badBuildTarget", "Compile")
    val id = nextId()
    svr.sendJsonRpc(
      s"""{ "jsonrpc": "2.0", "id": "$id", "method": "buildTarget/scalacOptions", "params": {
        |  "targets": [{ "uri": "$buildTarget" }, { "uri": "$badBuildTarget" }]
        |} }""".stripMargin
    )
    assert(processing("buildTarget/scalacOptions"))
    assert(svr.waitForString(10.seconds) { s =>
      (s contains s""""id":"$id"""") &&
      (s contains "scala-library-2.13.8.jar")
    })
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
    assert(processing("buildTarget/cleanCache"))
    val res = svr.waitFor[CleanCacheResult](10.seconds)
    assert(res.cleaned)
    assert(targetDir.list().isEmpty)
  }

  test("buildTarget/cleanCache: rebuild project") { _ =>
    svr.sendJsonRpc(
      s"""{ "jsonrpc": "2.0", "id": "${nextId()}", "method": "workspace/buildTargets", "params": {} }"""
    )
    assert(processing("workspace/buildTargets"))
    val result = svr.waitFor[WorkspaceBuildTargetsResult](10.seconds)
    val allTargets = result.targets.map(_.id.uri)

    svr.sendJsonRpc(
      s"""{ "jsonrpc": "2.0", "id": "${nextId()}", "method": "buildTarget/cleanCache", "params": {
         |  "targets": [
         |    ${allTargets.map(uri => s"""{ "uri": "$uri" }""").mkString(",\n")}
         |  ]
         |} }""".stripMargin
    )
    assert(processing("buildTarget/cleanCache"))
    val res = svr.waitFor[CleanCacheResult](10.seconds)
    assert(res.cleaned)
  }

  test("workspace/reload") { _ =>
    val id = nextId()
    svr.sendJsonRpc(
      s"""{ "jsonrpc": "2.0", "id": "$id", "method": "workspace/reload"}"""
    )
    assert(processing("workspace/reload"))
    assert(svr.waitForString(10.seconds) { s =>
      (s contains s""""id":"$id"""") &&
      (s contains """"result":null""")
    })
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
    val id = nextId()
    // reload
    reloadWorkspace(id)
    assert(
      svr.waitForString(10.seconds) { s =>
        s.contains(s""""buildTarget":{"uri":"$metaBuildTarget"}""") &&
        s.contains(s""""textDocument":{"uri":"${otherBuildFile.toPath.toUri}"}""") &&
        s.contains(""""severity":1""") &&
        s.contains(""""reset":true""")
      }
    )
    assert(
      svr.waitForString(10.seconds) { s =>
        s.contains(s""""id":"$id"""") &&
        s.contains(""""error"""") &&
        s.contains(s""""code":${ErrorCodes.InternalError}""") &&
        s.contains("Type error in expression")
      }
    )
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
    assert(
      svr.waitForString(10.seconds) { s =>
        s.contains(s""""buildTarget":{"uri":"$metaBuildTarget"}""") &&
        s.contains(s""""textDocument":{"uri":"${otherBuildFile.toPath.toUri}"}""") &&
        s.contains(""""diagnostics":[]""") &&
        s.contains(""""reset":true""")
      }
    )
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
    assert(processing("buildTarget/scalaMainClasses"))
    assert(svr.waitForString(30.seconds) { s =>
      (s contains s""""id":"$id"""") &&
      (s contains """"class":"main.Main"""")
    })
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
    assert(processing("buildTarget/run"))
    assert(svr.waitForString(10.seconds) { s =>
      (s contains "build/logMessage") &&
      (s contains """"message":"Hello World!"""")
    })
    assert(svr.waitForString(10.seconds) { s =>
      (s contains s""""id":"$id"""") &&
      (s contains """"statusCode":1""")
    })
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
    assert(processing("buildTarget/jvmRunEnvironment"))
    assert {
      svr.waitForString(10.seconds) { s =>
        (s contains s""""id":"$id"""") &&
        (s contains "jsoniter-scala-core_2.13-2.13.11.jar") && // compile dependency
        (s contains "\"jvmOptions\":[\"Xmx256M\"]") &&
        (s contains "\"environmentVariables\":{\"KEY\":\"VALUE\"}") &&
        (s contains "/buildserver/run-and-test/") // working directory
      }
    }
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
    assert(processing("buildTarget/jvmTestEnvironment"))
    assert {
      svr.waitForString(10.seconds) { s =>
        (s contains s""""id":"$id"""") &&
        // test depends on compile so it has dependencies from both
        (s contains "jsoniter-scala-core_2.13-2.13.11.jar") && // compile dependency
        (s contains "scalatest_2.13-3.0.8.jar") && // test dependency
        (s contains "\"jvmOptions\":[\"Xmx512M\"]") &&
        (s contains "\"environmentVariables\":{\"KEY_TEST\":\"VALUE_TEST\"}")
      }
    }
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
    assert(processing("buildTarget/scalaTestClasses"))
    assert(svr.waitForString(10.seconds) { s =>
      (s contains s""""id":"$id"""") &&
      (s contains """"tests.FailingTest"""") &&
      (s contains """"tests.PassingTest"""") &&
      (s contains """"framework":"ScalaTest"""")
    })
  }

  test("buildTarget/test: run all tests") { _ =>
    val buildTarget = buildTargetUri("runAndTest", "Test")
    val id = nextId()
    svr.sendJsonRpc(
      s"""{ "jsonrpc": "2.0", "id": "$id", "method": "buildTarget/test", "params": {
         |  "targets": [{ "uri": "$buildTarget" }]
         |} }""".stripMargin
    )
    assert(processing("buildTarget/test"))
    assert(svr.waitForString(10.seconds) { s =>
      (s contains s""""id":"$id"""") &&
      (s contains """"statusCode":2""")
    })
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
    assert(processing("buildTarget/test"))
    assert(svr.waitForString(10.seconds) { s =>
      (s contains s""""id":"$id"""") &&
      (s contains """"statusCode":1""")
    })
  }

  test("buildTarget/compile: report error") { _ =>
    val buildTarget = buildTargetUri("reportError", "Compile")
    compile(buildTarget)
    assert(svr.waitForString(10.seconds) { s =>
      (s contains s""""buildTarget":{"uri":"$buildTarget"}""") &&
      (s contains """"severity":1""") &&
      (s contains """"reset":true""")
    })
  }

  test("buildTarget/compile: report warning") { _ =>
    val buildTarget = buildTargetUri("reportWarning", "Compile")
    compile(buildTarget)
    assert(svr.waitForString(10.seconds) { s =>
      (s contains s""""buildTarget":{"uri":"$buildTarget"}""") &&
      (s contains """"severity":2""") &&
      (s contains """"reset":true""")
    })
  }

  test("buildTarget/compile: respond error") { _ =>
    val buildTarget = buildTargetUri("respondError", "Compile")
    val id = nextId()
    compile(buildTarget, id)
    assert(svr.waitForString(10.seconds) { s =>
      s.contains(s""""id":"$id"""") &&
      s.contains(""""error"""") &&
      s.contains(s""""code":${ErrorCodes.InternalError}""") &&
      s.contains("custom message")
    })
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
    assert(processing("buildTarget/resources"))
    assert(svr.waitForString(10.seconds) { s =>
      (s contains s""""id":"$id"""") && (s contains "util/src/main/resources/")
    })
  }

  test("buildTarget/outputPaths") { _ =>
    val buildTarget = buildTargetUri("util", "Compile")
    val badBuildTarget = buildTargetUri("badBuildTarget", "Compile")
    svr.sendJsonRpc(
      s"""{ "jsonrpc": "2.0", "id": "${nextId()}", "method": "buildTarget/outputPaths", "params": {
         |  "targets": [{ "uri": "$buildTarget" }, { "uri": "$badBuildTarget" }]
         |} }""".stripMargin
    )
    assert(processing("buildTarget/outputPaths"))
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

  private def initializeRequest(id: Int): Unit = {
    val params = InitializeBuildParams(
      "test client",
      "1.0.0",
      "2.1.0-M1",
      new URI("file://root/"),
      BuildClientCapabilities(Vector("scala")),
      None
    )
    svr.sendJsonRpc(request(id, "build/initialize", params))
  }

  private def processing(method: String, debug: Boolean = false): Boolean = {
    svr.waitForString(10.seconds) { msg =>
      if (debug) println(msg)
      msg.contains("build/logMessage") &&
      msg.contains(s""""message":"Processing $method"""")
    }
  }

  private def reloadWorkspace(id: Int = nextId()): Unit =
    svr.sendJsonRpc(s"""{ "jsonrpc": "2.0", "id": "$id", "method": "workspace/reload"}""")

  private def compile(buildTarget: URI, id: Int = nextId()): Unit = {
    val params =
      CompileParams(targets = Vector(BuildTargetIdentifier(buildTarget)), None, Vector.empty)
    svr.sendJsonRpc(request(id, "buildTarget/compile", params))
  }

  private def buildTargetSources(buildTargets: Seq[URI], id: Int = nextId()): String = {
    val targets = buildTargets.map(BuildTargetIdentifier.apply).toVector
    request(id, "buildTarget/sources", SourcesParams(targets))
  }

  private def request[T: JsonWriter](id: Int, method: String, params: T): String = {
    val request = JsonRpcRequestMessage("2.0", id.toString, method, Converter.toJson(params).get)
    val json = Converter.toJson(request).get
    CompactPrinter(json)
  }

  private def buildTargetUri(project: String, config: String): URI =
    new URI(s"${svr.baseDirectory.getAbsoluteFile.toURI}#$project/$config")

  private def metaBuildTarget: String =
    s"${svr.baseDirectory.getAbsoluteFile.toURI}project/#buildserver-build/Compile"
}
