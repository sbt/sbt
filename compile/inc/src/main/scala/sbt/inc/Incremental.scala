/* sbt -- Simple Build Tool
 * Copyright 2010  Mark Harrah
 */
package sbt
package inc

import xsbt.api.{ NameChanges, SameAPI, TopLevel }
import annotation.tailrec
import xsbti.api.{ Compilation, Source }
import xsbti.compile.DependencyChanges
import java.io.File

/**
 * Helper class to run incremental compilation algorithm.
 *
 *
 * This class delegates down to
 * - IncrementalNameHashing
 * - IncrementalDefault
 * - IncrementalAnyStyle
 */
object Incremental {
  class PrefixingLogger(val prefix: String)(orig: Logger) extends Logger {
    def trace(t: => Throwable): Unit = orig.trace(t)
    def success(message: => String): Unit = orig.success(message)
    def log(level: sbt.Level.Value, message: => String): Unit = orig.log(level, message.replaceAll("(?m)^", prefix))
  }

  /**
   * Runs the incremental compiler algorithm.
   *
   * @param sources   The sources to compile
   * @param entry  The means of looking up a class on the classpath.
   * @param previous The previously detected source dependencies.
   * @param current  A mechanism for generating stamps (timestamps, hashes, etc).
   * @param doCompile  The function which can run one level of compile.
   * @param log  The log where we write debugging information
   * @param options  Incremental compilation options
   * @param equivS  The means of testing whether two "Stamps" are the same.
   * @return
   *         A flag of whether or not compilation completed succesfully, and the resulting dependency analysis object.
   */
  def compile(sources: Set[File],
    entry: String => Option[File],
    previous: Analysis,
    current: ReadStamps,
    forEntry: File => Option[Analysis],
    doCompile: (Set[File], DependencyChanges) => Analysis,
    log: Logger,
    options: IncOptions)(implicit equivS: Equiv[Stamp]): (Boolean, Analysis) =
    {
      val incremental: IncrementalCommon =
        if (options.nameHashing)
          new IncrementalNameHashing(new PrefixingLogger("[naha] ")(log), options)
        else if (options.antStyle)
          new IncrementalAntStyle(log, options)
        else
          new IncrementalDefaultImpl(log, options)
      val initialChanges = incremental.changedInitial(entry, sources, previous, current, forEntry)
      val binaryChanges = new DependencyChanges {
        val modifiedBinaries = initialChanges.binaryDeps.toArray
        val modifiedClasses = initialChanges.external.allModified.toArray
        def isEmpty = modifiedBinaries.isEmpty && modifiedClasses.isEmpty
      }
      val initialInv = incremental.invalidateInitial(previous.relations, initialChanges)
      log.debug("All initially invalidated sources: " + initialInv + "\n")
      val analysis = manageClassfiles(options) { classfileManager =>
        incremental.cycle(initialInv, sources, binaryChanges, previous, doCompile, classfileManager, 1)
      }
      (initialInv.nonEmpty, analysis)
    }

  // the name of system property that was meant to enable debugging mode of incremental compiler but
  // it ended up being used just to enable debugging of relations. That's why if you migrate to new
  // API for configuring incremental compiler (IncOptions) it's enough to control value of `relationsDebug`
  // flag to achieve the same effect as using `incDebugProp`.
  @deprecated("Use `IncOptions.relationsDebug` flag to enable debugging of relations.", "0.13.2")
  val incDebugProp = "xsbt.inc.debug"

  private[inc] val apiDebugProp = "xsbt.api.debug"
  private[inc] def apiDebug(options: IncOptions): Boolean = options.apiDebug || java.lang.Boolean.getBoolean(apiDebugProp)

  private[sbt] def prune(invalidatedSrcs: Set[File], previous: Analysis): Analysis =
    prune(invalidatedSrcs, previous, ClassfileManager.deleteImmediately())

  private[sbt] def prune(invalidatedSrcs: Set[File], previous: Analysis, classfileManager: ClassfileManager): Analysis =
    {
      classfileManager.delete(invalidatedSrcs.flatMap(previous.relations.products))
      previous -- invalidatedSrcs
    }

  private[this] def manageClassfiles[T](options: IncOptions)(run: ClassfileManager => T): T =
    {
      val classfileManager = options.newClassfileManager()
      val result = try run(classfileManager) catch {
        case e: Exception =>
          classfileManager.complete(success = false)
          throw e
      }
      classfileManager.complete(success = true)
      result
    }

}
