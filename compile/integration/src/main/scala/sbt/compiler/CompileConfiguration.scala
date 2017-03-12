package sbt.compiler

import java.io.File

import sbt.CompileSetup
import sbt.inc.{ Analysis, IncOptions }
import sbt.inc.Locate._
import xsbti.Reporter
import xsbti.compile.{ CompileProgress, GlobalsCache }

/**
  * Configuration used for running an analyzing compiler (a compiler which can extract dependencies between source files and JARs).
  *
  * @param sources
  * @param classpath
  * @param previousAnalysis
  * @param previousSetup
  * @param currentSetup
  * @param progress
  * @param getAnalysis
  * @param definesClass
  * @param reporter
  * @param compiler
  * @param javac
  * @param cache
  * @param incOptions
  */
final class CompileConfiguration(val sources: Seq[File],
                                 val classpath: Seq[File],
                                 val previousAnalysis: Analysis,
                                 val previousSetup: Option[CompileSetup],
                                 val currentSetup: CompileSetup,
                                 val progress: Option[CompileProgress],
                                 val getAnalysis: File => Option[Analysis],
                                 val definesClass: DefinesClass,
                                 val reporter: Reporter,
                                 val compiler: AnalyzingCompiler,
                                 val javac: xsbti.compile.JavaCompiler,
                                 val cache: GlobalsCache,
                                 val incOptions: IncOptions)
