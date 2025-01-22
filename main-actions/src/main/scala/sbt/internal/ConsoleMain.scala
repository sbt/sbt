package sbt
package internal

import java.io.File
import java.nio.file.Paths
// import org.jline.terminal.{ Terminal as JTerminal }
import sbt.internal.inc.{ AnalyzingCompiler, ScalaInstance, ZincUtil }
import sbt.internal.inc.classpath.ClasspathUtil
import sbt.internal.worker.ConsoleConfig
import sbt.io.IO
import sbt.util.{ LogExchange, Logger }
import sjsonnew.support.scalajson.unsafe.{ Parser, Converter }
import xsbti.compile.ClasspathOptionsUtil
// import sbt.internal.util.{ DeprecatedJLine } // Terminal as ITerminal

class ConsoleMain:
  def run(config: ConsoleConfig): Unit =
    val si = scalaInstance(config)
    val compiler = analyzingCompiler(config, si)
    val console = new Console(compiler)
    given log: Logger = LogExchange.logger("console")
    val externalCp = config.externalDependencyJars.map(Paths.get(_))
    IO.withTemporaryDirectory: tempDir =>
      val loader = ClasspathUtil.makeLoader(externalCp, si, tempDir.toPath())
      // DeprecatedJLine.setTerminalOverride(jline3)
      console(
        classpath = externalCp.map(_.toFile),
        options = Nil,
        loader = loader,
        initialCommands = "",
        cleanupCommands = "",
      )()

  // def jline3: JTerminal =
  //   val term =
  //     org.jline.terminal.TerminalBuilder
  //       .builder()
  //       .system(false)
  //       // .paused(true)
  //       .build()
  //   term

  def analyzingCompiler(config: ConsoleConfig, si: ScalaInstance): AnalyzingCompiler =
    val bridgeProvider = ZincUtil.constantBridgeProvider(si, new File(config.bridgeJar))
    val classpathOptions = ClasspathOptionsUtil.auto()
    AnalyzingCompiler(
      si,
      bridgeProvider,
      classpathOptions,
      _ => (),
      None
    )

  def scalaInstance(config: ConsoleConfig): ScalaInstance =
    val siConfig = config.scalaInstanceConfig
    val libraryJars = siConfig.libraryJars.map(Paths.get(_)).sortBy(_.getFileName.toString())
    val allCompilerJars =
      siConfig.allCompilerJars
        .map(Paths.get(_))
        .sortBy(_.getFileName.toString())
    val jlineJars = allCompilerJars.filter(_.getFileName.toString().contains("jline"))
    val compilerJars =
      allCompilerJars.filterNot(x => libraryJars.contains(x) || jlineJars.contains(x)).distinct
    val allDocJars = siConfig.allDocJars.map(Paths.get(_)).sortBy(_.getFileName.toString())
    val docJars = allDocJars
      .filterNot(jar => libraryJars.contains(jar) || compilerJars.contains(jar))
      .distinct
    val allJars = libraryJars ++ compilerJars ++ docJars
    // val jlineLoader = ClasspathUtil.toLoader(jlineJars)
    val jlineLoader = classOf[org.jline.terminal.Terminal].getClassLoader()
    val currentLoader = classOf[ConsoleMain].getClassLoader()
    val libraryLoader = ClasspathUtil.toLoader(libraryJars, jlineLoader)
    val compilerLoader = ClasspathUtil.toLoader(compilerJars, libraryLoader)
    val fullLoader =
      if docJars.isEmpty then compilerLoader
      else ClasspathUtil.toLoader(docJars, compilerLoader)
    new ScalaInstance(
      version = siConfig.scalaVersion,
      loader = fullLoader,
      loaderCompilerOnly = compilerLoader,
      loaderLibraryOnly = libraryLoader,
      libraryJars = libraryJars.map(_.toFile).toArray,
      compilerJars = compilerJars.map(_.toFile).toArray,
      allJars = allJars.map(_.toFile).toArray,
      explicitActual = Some(siConfig.scalaVersion)
    )
end ConsoleMain

object ConsoleMain:
  def main(args: Array[String]): Unit =
    args.toList match
      case Nil => println("ConsoleMain")
      case arg :: Nil if arg.startsWith("@") =>
        import sbt.internal.worker.codec.JsonProtocol.given
        val arg1 = arg.drop(1)
        val s = IO.read(File(arg1))
        val json = Parser.parseFromString(s).get
        val config = Converter.fromJson[ConsoleConfig](json).get
        val main = ConsoleMain()
        main.run(config)
      case _ => sys.exit(1)
end ConsoleMain
