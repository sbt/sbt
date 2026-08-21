package sbt
package internal

import java.io.{ File, PrintStream }
import java.nio.file.{ Path, Paths }
import java.util.Optional
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Function as JavaFunction
import sbt.internal.inc.{
  AnalyzingCompiler,
  ScalaInstance,
  Locate,
  IncrementalCompilerImpl,
  MappedFileConverter,
  ManagedLoggedReporter,
  Stamps,
  ZincUtil,
}
import sbt.internal.inc.classpath.ClasspathUtil
import sbt.internal.inc.JavaInterfaceUtil.*
import sbt.internal.worker.{ CompileConfig, CompileResponse, ScalaInstanceConfig }
import sbt.internal.worker.codec.JsonProtocol.given
import sbt.internal.util.{ ManagedLogger, ConsoleOut, MainAppender }
import sbt.io.IO
import sbt.util.{ LoggerContext, Level }
import scala.util.control.NonFatal
import sjsonnew.support.scalajson.unsafe.{ CompactPrinter, Parser, Converter }
import xsbti.{ CompileFailed, HashedVirtualFileRef, Position, T2, VirtualFile }
import xsbti.compile.{ ScalaInstance as _, * }

object CompileMain:
  private lazy val zinc: IncrementalCompilerImpl = new IncrementalCompilerImpl

  def run(config: CompileConfig, id: Long, jsonOut: PrintStream): Unit =
    try
      val conv = MappedFileConverter(
        Map((config.fileConverterConfig.rootPaths.map: x =>
          (x.name, Paths.get(x.value)))*),
        true
      )
      val si = scalaInstance(config.scalaInstanceConfig)
      val bridgeJars = config.bridgeJars.map(Paths.get)
      val scalac = analyzingCompiler(bridgeJars, si)
      val co = ClasspathOptionsUtil.noboot(config.scalaInstanceConfig.scalaVersion)
      val cs = ZincUtil.compilers(
        instance = si,
        classpathOptions = co,
        javaHome = None,
        scalac = scalac,
      )
      val analysisFile = Paths.get(config.analysisFile)
      val setup = incSetup(config)
      val log = mkLogger(Level.Warn)
      val output = Paths.get(config.output)
      val sources = config.sources.map(Paths.get(_)).map(conv.toVirtualFile)
      val cp = config.externalDependencyJars.map(Paths.get(_))
      val cpVf = cp.map(conv.toVirtualFile)
      val in = zinc.inputs(
        classpath = cpVf.toArray,
        sources = sources.toArray,
        classesDirectory = output,
        earlyJarPath = config.earlyJarPath.map(Paths.get),
        scalacOptions = config.scalacOptions.toArray,
        javacOptions = config.javacOptions.toArray,
        maxErrors = config.maxErrors,
        sourcePositionMappers = Array.empty[JavaFunction[Position, Optional[Position]]],
        order = CompileOrder.valueOf(config.compileOrder),
        compilers = cs,
        setup = setup,
        pr = previousResult(analysisFile),
        temporaryClassesDirectory = jnone[Path],
        converter = conv,
        stampReader = Stamps.timeWrapBinaryStamps(conv),
      )
      val r = zinc.compile(in, log)
      val store = FileAnalysisStore.getDefault(analysisTmpPath(analysisFile).toFile)
      store.set(AnalysisContents.create(r.getAnalysis, r.getMiniSetup))
      val response = CompileResponse(
        hasModified = r.hasModified
      )
      val resJson = Converter.toJson(response).get
      val json = CompactPrinter(resJson)
      jsonOut.println(jsonRpcResponse(id, json))
      jsonOut.flush()
    catch
      case e: CompileFailed =>
        jsonOut.println(jsonRpcError(id, 1009, "compilation failed"))
        jsonOut.flush()
      case NonFatal(e) =>
        e.printStackTrace()
        jsonOut.println(jsonRpcError(id, 1, e.toString))
        jsonOut.flush()

  private def jsonRpcResponse(id: Long, result: String): String =
    s"""{ "jsonrpc": "2.0", "result": $result, "id": $id }"""

  private def jsonRpcError(id: Long, code: Int, err: String): String =
    val escaped = err
      .replace("\\", "\\\\")
      .replace("\"", "\\\"")
      .replace("\n", "\\n")
      .replace("\r", "\\r")
      .replace("\t", "\\t")
    s"""{ "jsonrpc": "2.0", "error": { "code": $code, "message": "$escaped" }, "id": $id }"""

  private def incSetup(config: CompileConfig): Setup =
    val analysisFile = Paths.get(config.analysisFile)
    val analysisForCp: Map[HashedVirtualFileRef, Path] = Map((config.analysisMap.map: pair =>
      pair.name -> Paths.get(pair.value))*)
    val lookup: PerClasspathEntryLookup = new PerClasspathEntryLookup:
      def read(p: Path): Option[CompileAnalysis] =
        FileAnalysisStore.getDefault(p.toFile).get().toOption.map(_.getAnalysis)
      override def analysis(cpEntry: VirtualFile): Optional[CompileAnalysis] =
        analysisForCp.get(cpEntry).flatMap(read).toOptional
      override def definesClass(cpEntry: VirtualFile): DefinesClass = Locate.definesClass(cpEntry)
    val incOptions = IncOptions.of()
    val log = mkLogger(Level.Warn)
    val reporter = ManagedLoggedReporter(100, log)
    val earlyStore =
      config.earlyAnalysisFile.map(u => FileAnalysisStore.getDefault(Paths.get(u).toFile))
    Setup.of(
      lookup,
      false,
      analysisFile,
      CompilerCache.fresh(),
      incOptions,
      reporter,
      jnone,
      earlyStore.toOptional,
      Array[T2[String, String]](),
    )

  def analysisTmpPath(analysisFile: Path): Path =
    analysisFile.resolveSibling(analysisFile.getFileName.toString + ".tmp")

  lazy val console = ConsoleOut.systemOut
  lazy val consoleAppender = MainAppender.defaultScreen(console)
  val generateId: AtomicInteger = new AtomicInteger
  private def mkLogger(level: Level.Value): ManagedLogger =
    val loggerName = "compile-" + generateId.incrementAndGet
    val l = LoggerContext.globalContext.logger(loggerName, None, None)
    LoggerContext.globalContext.clearAppenders(loggerName)
    LoggerContext.globalContext.addAppender(loggerName, consoleAppender -> level)
    l

  def none[A]: Option[A] = None: Option[A]
  def jnone[A]: Optional[A] = none[A].toOptional

  private def analyzingCompiler(bridgeJars: Seq[Path], si: ScalaInstance): AnalyzingCompiler =
    val bridgeProvider = ZincUtil.constantBridgeProvider(si, bridgeJars.head.toFile)
    val classpathOptions = ClasspathOptionsUtil.auto()
    AnalyzingCompiler(
      si,
      bridgeProvider,
      classpathOptions,
      _ => (),
      None
    )

  def scalaInstance(config: ScalaInstanceConfig): ScalaInstance =
    val libraryJars = config.libraryJars.map(Paths.get).sortBy(_.getFileName.toString())
    val allCompilerJars =
      config.allCompilerJars
        .map(Paths.get)
        .sortBy(_.getFileName.toString())
    val jlineJars = allCompilerJars.filter(_.getFileName.toString.contains("jline"))
    val compilerJars =
      allCompilerJars.filterNot(x => libraryJars.contains(x) || jlineJars.contains(x)).distinct
    val extraToolJars0 = config.extraToolJars.map(Paths.get).sortBy(_.getFileName.toString())
    val extraToolJars = extraToolJars0
      .filterNot(jar => libraryJars.contains(jar) || compilerJars.contains(jar))
      .distinct
    val allJars = libraryJars ++ compilerJars ++ extraToolJars
    val topLoader = classOf[Compilers].getClassLoader
    val libraryLoader = ClasspathUtil.toLoader(libraryJars, topLoader)
    val compilerLoader = ClasspathUtil.toLoader(compilerJars, libraryLoader)
    val fullLoader =
      if extraToolJars.isEmpty then compilerLoader
      else ClasspathUtil.toLoader(extraToolJars, compilerLoader)
    new ScalaInstance(
      version = config.scalaVersion,
      loader = fullLoader,
      loaderCompilerOnly = compilerLoader,
      loaderLibraryOnly = libraryLoader,
      libraryJars = libraryJars.map(_.toFile).toArray,
      compilerJars = compilerJars.map(_.toFile).toArray,
      allJars = allJars.map(_.toFile).toArray,
      explicitActual = Some(config.scalaVersion)
    )

  def previousResult(analysisFile: Path): PreviousResult =
    val store = FileAnalysisStore.getDefault(analysisFile.toFile)
    store.get().toOption match
      case Some(contents) =>
        val analysis = Option(contents.getAnalysis).toOptional
        val setup = Option(contents.getMiniSetup).toOptional
        PreviousResult.of(analysis, setup)
      case None => PreviousResult.of(jnone[CompileAnalysis], jnone[MiniSetup])

  def main(args: Array[String], id: java.lang.Long, jsonOut: PrintStream): Unit =
    args.toList match
      case Nil               => println("CompileMain")
      case s"@${arg}" :: Nil =>
        val s = IO.read(File(arg))
        val json = Parser.parseFromString(s).get
        val config = Converter.fromJson[CompileConfig](json).get
        run(config, id, jsonOut)
      case xs => sys.error(s"unknown args: $xs")
end CompileMain
