package sbt
package internal

import java.nio.file.{ Path, Paths }
import java.net.URLClassLoader
import java.util.ArrayList
import org.scalasbt.shadedgson.com.google.gson.JsonObject
import sbt.OptionSyntax.*
import sbt.internal.worker.{
  CompileConfig,
  CompileResponse,
  FileConverterConfig,
  HVFRURI,
  ScalaInstanceConfig,
  StringURI,
}
import sbt.internal.util.Attributed
import sbt.internal.worker.codec.JsonProtocol.given
import sbt.internal.worker1.{ FilePath, RunInfo, WorkerMain }
import sbt.io.IO
import sbt.util.Logger

import scala.jdk.CollectionConverters.*
import scala.util.Random
import scala.sys.process.Process
import sjsonnew.support.scalajson.unsafe.{ Converter, CompactPrinter, Parser }
import xsbti.compile.*
import xsbti.{ HashedVirtualFileRef, VirtualFileRef }

private[sbt] object ForkCompile:
  val r = Random()

  def compile(
      s: Keys.TaskStreams,
      fo: ForkOptions,
      rs: Map[String, Path],
      in: Inputs,
      sic: ScalaInstanceConfig,
      bridges: Seq[HashedVirtualFileRef],
      analysisFile: Path,
      acs: Seq[Attributed[HashedVirtualFileRef]],
  ): CompileResult =
    val g = WorkerMain.mkGson()
    val ct = WorkerConnection.Tcp
    val w = WorkerExchange.startWorker(fo, Nil, ct)
    val randomId = r.nextLong()
    val wl = ForkCompile.React(randomId, s.log, w.process)
    try
      WorkerExchange.registerListener(wl)
      wl.notifyExit(w.process)
      val cpList = ArrayList[FilePath](
        (currentClasspath
          .map: p =>
            FilePath(p.toUri(), ""))
          .asJava
      )
      val fcConfig = FileConverterConfig(
        rootPaths = rs.toSeq.toVector.map((k, v) => StringURI(k, v.toUri()))
      )
      val conv = in.options().converter().get()
      val sources = in.options().sources().toVector.map(conv.toPath)
      val cp = in.options().classpath().toVector.map(conv.toPath)
      val earlyJarPath = for
        o <- in.options().earlyOutput().asScala
        single <- o.getSingleOutputAsPath().asScala
      yield single
      val analysisMap = for
        entry <- acs.toVector
        ref <- entry.metadata.get(Keys.analysis)
      yield HVFRURI(entry.data, conv.toPath(VirtualFileRef.of(ref)).toUri())
      // pseudo case class that is used to transport the server knowledge to the
      // forked worker process.
      val config = CompileConfig(
        fileConverterConfig = fcConfig,
        scalaInstanceConfig = sic,
        bridgeJars = bridges.toVector.map(vf => conv.toPath(vf).toUri()),
        sources = sources.map(_.toString()),
        externalDependencyJars = cp.map(_.toString()),
        output = in.options().classesDirectory().toUri(),
        analysisFile = analysisFile.toUri(),
        earlyJarPath = earlyJarPath.map(_.toUri()),
        scalacOptions = in.options().scalacOptions().toVector,
        javacOptions = in.options().javacOptions().toVector,
        maxErrors = in.options().maxErrors(),
        analysisMap = analysisMap,
        compileOrder = in.options().order().name(),
      )
      val configJson = Converter.toJson[CompileConfig](config).get
      IO.withTemporaryDirectory: tempDir =>
        val params = tempDir.toPath().resolve("params.json")
        IO.write(params.toFile(), CompactPrinter(configJson))
        val param = RunInfo(
          true,
          RunInfo.JvmRunInfo(
            ArrayList(List(s"@$params").asJava),
            cpList,
            "sbt.internal.CompileMain",
            false,
          ),
          null
        )
        val paramJson = g.toJson(param, param.getClass)
        val json = jsonRpcRequest(randomId, "compile", paramJson)
        w.println(json)
        val response = wl.blockForResponse()
        val store = FileAnalysisStore.getDefault(analysisFile.toFile())
        val contents = store.get().get()
        CompileResult.of(
          contents.getAnalysis(),
          contents.getMiniSetup(),
          response.hasModified,
        )
    finally
      WorkerExchange.unregisterListener(wl)
      w.close()

  def currentClasspath: List[Path] =
    val urls = classOf[CompileMain.type].getClassLoader() match
      case cl: URLClassLoader => cl.getURLs().toList.map(u => Paths.get(u.toURI()))
      case _                  =>
        sys
          .props("java.class.path")
          .split(java.io.File.pathSeparator)
          .toList
          .filter(_.nonEmpty)
          .map(Paths.get(_))
    urls ++ Vector(
      IO.classLocationPath(classOf[xsbti.compile.ScalaInstance]),
      IO.classLocationPath(classOf[xsbti.Logger]),
      IO.classLocationPath(classOf[jline.Terminal]),
      IO.classLocationPath(classOf[org.jline.utils.InfoCmp]),
    )

  private def jsonRpcRequest(id: Long, method: String, params: String): String =
    s"""{ "jsonrpc": "2.0", "method": "$method", "params": $params, "id": $id }"""

  private class React(id: Long, log: Logger, process: Process)
      extends ProcessReact[CompileResponse](id, log, process):
    override def processResponse(o: JsonObject): Unit =
      val s = o.getAsJsonObject("result")
      val json = Parser.parseFromString(s.toString()).get
      val result = Converter.fromJson[CompileResponse](json).get
      promise.success(result)

    override def processNotification(o: JsonObject): Unit = ()
end ForkCompile
