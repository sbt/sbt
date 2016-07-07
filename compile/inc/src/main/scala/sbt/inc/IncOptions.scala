package sbt.inc

import java.io.File

/**
 * Represents all configuration options for the incremental compiler itself and
 * not the underlying Java/Scala compiler.
 *
 * NOTE: This class used to be a case class but due to problems with retaining
 * binary compatibility while new fields are added it has been expanded to a
 * regular class. All compiler-generated methods for a case class has been
 * defined explicitly.
 */
final class IncOptions(
    /** After which step include whole transitive closure of invalidated source files. */
    val transitiveStep: Int,
    /**
     * What's the fraction of invalidated source files when we switch to recompiling
     * all files and giving up incremental compilation altogether. That's useful in
     * cases when probability that we end up recompiling most of source files but
     * in multiple steps is high. Multi-step incremental recompilation is slower
     * than recompiling everything in one step.
     */
    val recompileAllFraction: Double,
    /** Print very detailed information about relations, such as dependencies between source files. */
    val relationsDebug: Boolean,
    /**
     * Enable tools for debugging API changes. At the moment this option is unused but in the
     * future it will enable for example:
     *   - disabling API hashing and API minimization (potentially very memory consuming)
     *   - diffing textual API representation which helps understanding what kind of changes
     *     to APIs are visible to the incremental compiler
     */
    val apiDebug: Boolean,
    /**
     * Controls context size (in lines) displayed when diffs are produced for textual API
     * representation.
     *
     * This option is used only when `apiDebug == true`.
     */
    val apiDiffContextSize: Int,
    /**
     * The directory where we dump textual representation of APIs. This method might be called
     * only if apiDebug returns true. This is unused option at the moment as the needed functionality
     * is not implemented yet.
     */
    val apiDumpDirectory: Option[java.io.File],
    /** Creates a new ClassfileManager that will handle class file deletion and addition during a single incremental compilation run. */
    val newClassfileManager: () => ClassfileManager,
    /**
     * Determines whether incremental compiler should recompile all dependencies of a file
     * that contains a macro definition.
     */
    val recompileOnMacroDef: Boolean,
    /**
     * Determines whether incremental compiler uses the new algorithm known as name hashing.
     *
     * This flag is disabled by default so incremental compiler's behavior is the same as in sbt 0.13.0.
     *
     * IMPLEMENTATION NOTE:
     * Enabling this flag enables a few additional functionalities that are needed by the name hashing algorithm:
     *
     *   1. New dependency source tracking is used. See `sbt.inc.Relations` for details.
     *   2. Used names extraction and tracking is enabled. See `sbt.inc.Relations` for details as well.
     *   3. Hashing of public names is enabled. See `sbt.inc.AnalysisCallback` for details.
     *
     */
    val nameHashing: Boolean,
    /**
     * THE `antStyle` OPTION IS UNSUPPORTED, MAY GO AWAY AT ANY POINT.
     *
     * Enables "ant-style" mode of incremental compilation. This mode emulates what Ant's scalac command does.
     * The idea is to recompile just changed source files and not perform any invalidation of dependencies. This
     * is a very naive mode of incremental compilation that very often leads to broken binaries.
     *
     * The Ant-style mode has been introduced because Scala team needs it for migration of Scala compiler to sbt.
     * The name hashing algorithm doesn't work well with Scala compiler sources due to deep inheritance chains.
     * There's a plan to refactor compiler's code to use more composition instead of inheritance.
     *
     * Once Scala compiler sources are refactored to work well with name hashing algorithm this option will be
     * deleted immediately.
     */
    val antStyle: Boolean,
    /**
     * Include synthetic methods into the dependency tracking by name hashing.
     */
    val includeSynthToNameHashing: Boolean,
    /** Determines whether to log information on file recompiled due to a transitive macro change */
    val logRecompileOnMacro: Boolean) extends Product with Serializable {

  /**
   * Secondary constructor introduced to make IncOptions to be binary compatible with version that didn't have
   * `recompileOnMacroDef` and `nameHashing` fields defined.
   */
  def this(transitiveStep: Int, recompileAllFraction: Double, relationsDebug: Boolean, apiDebug: Boolean,
    apiDiffContextSize: Int, apiDumpDirectory: Option[java.io.File], newClassfileManager: () => ClassfileManager) = {
    this(transitiveStep, recompileAllFraction, relationsDebug, apiDebug, apiDiffContextSize,
      apiDumpDirectory, newClassfileManager, IncOptions.recompileOnMacroDefDefault, IncOptions.nameHashingDefault,
      IncOptions.antStyleDefault, IncOptions.includeSynthToNameHashingDefault,
      IncOptions.logRecompileOnMacroDefault)
  }

  def this(transitiveStep: Int, recompileAllFraction: Double, relationsDebug: Boolean, apiDebug: Boolean,
    apiDiffContextSize: Int, apiDumpDirectory: Option[java.io.File], newClassfileManager: () => ClassfileManager,
    recompileOnMacroDef: Boolean, nameHashing: Boolean, antStyle: Boolean) = {
    this(transitiveStep, recompileAllFraction, relationsDebug, apiDebug,
      apiDiffContextSize, apiDumpDirectory, newClassfileManager, recompileOnMacroDef, nameHashing,
      antStyle, IncOptions.includeSynthToNameHashingDefault, IncOptions.logRecompileOnMacroDefault)
  }

  assert(!(antStyle && nameHashing), "Name hashing and Ant-style cannot be enabled at the same time.")

  def withTransitiveStep(transitiveStep: Int): IncOptions = {
    new IncOptions(transitiveStep, recompileAllFraction, relationsDebug, apiDebug, apiDiffContextSize,
      apiDumpDirectory, newClassfileManager, recompileOnMacroDef, nameHashing, antStyle,
      includeSynthToNameHashing, logRecompileOnMacro)
  }

  def withRecompileAllFraction(recompileAllFraction: Double): IncOptions = {
    new IncOptions(transitiveStep, recompileAllFraction, relationsDebug, apiDebug, apiDiffContextSize,
      apiDumpDirectory, newClassfileManager, recompileOnMacroDef, nameHashing, antStyle,
      includeSynthToNameHashing, logRecompileOnMacro)
  }

  def withRelationsDebug(relationsDebug: Boolean): IncOptions = {
    new IncOptions(transitiveStep, recompileAllFraction, relationsDebug, apiDebug, apiDiffContextSize,
      apiDumpDirectory, newClassfileManager, recompileOnMacroDef, nameHashing, antStyle,
      includeSynthToNameHashing, logRecompileOnMacro)
  }

  def withApiDebug(apiDebug: Boolean): IncOptions = {
    new IncOptions(transitiveStep, recompileAllFraction, relationsDebug, apiDebug, apiDiffContextSize,
      apiDumpDirectory, newClassfileManager, recompileOnMacroDef, nameHashing, antStyle,
      includeSynthToNameHashing, logRecompileOnMacro)
  }

  def withApiDiffContextSize(apiDiffContextSize: Int): IncOptions = {
    new IncOptions(transitiveStep, recompileAllFraction, relationsDebug, apiDebug, apiDiffContextSize,
      apiDumpDirectory, newClassfileManager, recompileOnMacroDef, nameHashing, antStyle,
      includeSynthToNameHashing, logRecompileOnMacro)
  }

  def withApiDumpDirectory(apiDumpDirectory: Option[File]): IncOptions = {
    new IncOptions(transitiveStep, recompileAllFraction, relationsDebug, apiDebug, apiDiffContextSize,
      apiDumpDirectory, newClassfileManager, recompileOnMacroDef, nameHashing, antStyle,
      includeSynthToNameHashing, logRecompileOnMacro)
  }

  def withNewClassfileManager(newClassfileManager: () => ClassfileManager): IncOptions = {
    new IncOptions(transitiveStep, recompileAllFraction, relationsDebug, apiDebug, apiDiffContextSize,
      apiDumpDirectory, newClassfileManager, recompileOnMacroDef, nameHashing, antStyle,
      includeSynthToNameHashing, logRecompileOnMacro)
  }

  def withRecompileOnMacroDef(recompileOnMacroDef: Boolean): IncOptions = {
    new IncOptions(transitiveStep, recompileAllFraction, relationsDebug, apiDebug, apiDiffContextSize,
      apiDumpDirectory, newClassfileManager, recompileOnMacroDef, nameHashing, antStyle,
      includeSynthToNameHashing, logRecompileOnMacro)
  }

  def withNameHashing(nameHashing: Boolean): IncOptions = {
    new IncOptions(transitiveStep, recompileAllFraction, relationsDebug, apiDebug, apiDiffContextSize,
      apiDumpDirectory, newClassfileManager, recompileOnMacroDef, nameHashing, antStyle,
      includeSynthToNameHashing, logRecompileOnMacro)
  }

  def withIncludeSynthToNameHashing(includeSynthToNameHashing: Boolean): IncOptions = {
    new IncOptions(transitiveStep, recompileAllFraction, relationsDebug, apiDebug, apiDiffContextSize,
      apiDumpDirectory, newClassfileManager, recompileOnMacroDef, nameHashing, antStyle,
      includeSynthToNameHashing, logRecompileOnMacro)
  }

  def withAntStyle(antStyle: Boolean): IncOptions = {
    new IncOptions(transitiveStep, recompileAllFraction, relationsDebug, apiDebug, apiDiffContextSize,
      apiDumpDirectory, newClassfileManager, recompileOnMacroDef, nameHashing, antStyle,
      includeSynthToNameHashing, logRecompileOnMacro)
  }

  def withLogRecompileOnMacro(logRecompileOnMacro: Boolean): IncOptions = {
    new IncOptions(transitiveStep, recompileAllFraction, relationsDebug, apiDebug, apiDiffContextSize,
      apiDumpDirectory, newClassfileManager, recompileOnMacroDef, nameHashing, antStyle,
      includeSynthToNameHashing, logRecompileOnMacro)
  }

  //- EXPANDED CASE CLASS METHOD BEGIN -//
  @deprecated("Use `with$nameOfTheField` copying methods instead.", "0.13.2")
  def copy(transitiveStep: Int = this.transitiveStep, recompileAllFraction: Double = this.recompileAllFraction,
    relationsDebug: Boolean = this.relationsDebug, apiDebug: Boolean = this.apiDebug,
    apiDiffContextSize: Int = this.apiDiffContextSize,
    apiDumpDirectory: Option[java.io.File] = this.apiDumpDirectory,
    newClassfileManager: () => ClassfileManager = this.newClassfileManager): IncOptions = {
    new IncOptions(transitiveStep, recompileAllFraction, relationsDebug, apiDebug, apiDiffContextSize,
      apiDumpDirectory, newClassfileManager, recompileOnMacroDef, nameHashing, antStyle)
  }

  @deprecated("Methods generated for case class will be removed in the future.", "0.13.2")
  override def productPrefix: String = "IncOptions"

  @deprecated("Methods generated for case class will be removed in the future.", "0.13.2")
  def productArity: Int = 10

  @deprecated("Methods generated for case class will be removed in the future.", "0.13.2")
  def productElement(x$1: Int): Any = x$1 match {
    case 0 => IncOptions.this.transitiveStep
    case 1 => IncOptions.this.recompileAllFraction
    case 2 => IncOptions.this.relationsDebug
    case 3 => IncOptions.this.apiDebug
    case 4 => IncOptions.this.apiDiffContextSize
    case 5 => IncOptions.this.apiDumpDirectory
    case 6 => IncOptions.this.newClassfileManager
    case 7 => IncOptions.this.recompileOnMacroDef
    case 8 => IncOptions.this.nameHashing
    case 9 => IncOptions.this.antStyle
    case _ => throw new IndexOutOfBoundsException(x$1.toString())
  }

  @deprecated("Methods generated for case class will be removed in the future.", "0.13.2")
  override def productIterator: Iterator[Any] = scala.runtime.ScalaRunTime.typedProductIterator[Any](IncOptions.this)

  @deprecated("Methods generated for case class will be removed in the future.", "0.13.2")
  def canEqual(x$1: Any): Boolean = x$1.isInstanceOf[IncOptions]

  override def hashCode(): Int = {
    import scala.runtime.Statics
    var acc: Int = -889275714
    acc = Statics.mix(acc, transitiveStep)
    acc = Statics.mix(acc, Statics.doubleHash(recompileAllFraction))
    acc = Statics.mix(acc, if (relationsDebug) 1231 else 1237)
    acc = Statics.mix(acc, if (apiDebug) 1231 else 1237)
    acc = Statics.mix(acc, apiDiffContextSize)
    acc = Statics.mix(acc, Statics.anyHash(apiDumpDirectory))
    acc = Statics.mix(acc, Statics.anyHash(newClassfileManager))
    acc = Statics.mix(acc, if (recompileOnMacroDef) 1231 else 1237)
    acc = Statics.mix(acc, if (nameHashing) 1231 else 1237)
    acc = Statics.mix(acc, if (antStyle) 1231 else 1237)
    Statics.finalizeHash(acc, 9)
  }

  override def toString(): String = scala.runtime.ScalaRunTime._toString(IncOptions.this)

  override def equals(x$1: Any): Boolean = {
    this.eq(x$1.asInstanceOf[Object]) || (x$1.isInstanceOf[IncOptions] && ({
      val IncOptions$1: IncOptions = x$1.asInstanceOf[IncOptions]
      transitiveStep == IncOptions$1.transitiveStep && recompileAllFraction == IncOptions$1.recompileAllFraction &&
        relationsDebug == IncOptions$1.relationsDebug && apiDebug == IncOptions$1.apiDebug &&
        apiDiffContextSize == IncOptions$1.apiDiffContextSize && apiDumpDirectory == IncOptions$1.apiDumpDirectory &&
        newClassfileManager == IncOptions$1.newClassfileManager &&
        recompileOnMacroDef == IncOptions$1.recompileOnMacroDef && nameHashing == IncOptions$1.nameHashing &&
        antStyle == IncOptions$1.antStyle
    }))
  }
  //- EXPANDED CASE CLASS METHOD END -//
}

object IncOptions extends Serializable {
  private val recompileOnMacroDefDefault: Boolean = true
  private[sbt] val nameHashingDefault: Boolean = true
  private val antStyleDefault: Boolean = false
  // This should default to false
  private[sbt] val includeSynthToNameHashingDefault = java.lang.Boolean.getBoolean("sbt.inc.include_synth")
  private[sbt] val logRecompileOnMacroDefault = true
  val Default = IncOptions(
    //    1. recompile changed sources
    // 2(3). recompile direct dependencies and transitive public inheritance dependencies of sources with API changes in 1(2).
    //    4. further changes invalidate all dependencies transitively to avoid too many steps
    transitiveStep = 3,
    recompileAllFraction = 0.5,
    relationsDebug = false,
    apiDebug = false,
    apiDiffContextSize = 5,
    apiDumpDirectory = None,
    newClassfileManager = ClassfileManager.deleteImmediately,
    recompileOnMacroDef = recompileOnMacroDefDefault,
    nameHashing = nameHashingDefault
  )
  //- EXPANDED CASE CLASS METHOD BEGIN -//
  final override def toString(): String = "IncOptions"
  @deprecated("Use overloaded variant of `apply` with complete list of arguments instead.", "0.13.2")
  def apply(transitiveStep: Int, recompileAllFraction: Double, relationsDebug: Boolean, apiDebug: Boolean,
    apiDiffContextSize: Int, apiDumpDirectory: Option[java.io.File],
    newClassfileManager: () => ClassfileManager): IncOptions = {
    new IncOptions(transitiveStep, recompileAllFraction, relationsDebug, apiDebug, apiDiffContextSize,
      apiDumpDirectory, newClassfileManager)
  }
  def apply(transitiveStep: Int, recompileAllFraction: Double, relationsDebug: Boolean, apiDebug: Boolean,
    apiDiffContextSize: Int, apiDumpDirectory: Option[java.io.File],
    newClassfileManager: () => ClassfileManager, recompileOnMacroDef: Boolean,
    nameHashing: Boolean): IncOptions = {
    new IncOptions(transitiveStep, recompileAllFraction, relationsDebug, apiDebug, apiDiffContextSize,
      apiDumpDirectory, newClassfileManager, recompileOnMacroDef, nameHashing, antStyleDefault)
  }
  @deprecated("Methods generated for case class will be removed in the future.", "0.13.2")
  def unapply(x$0: IncOptions): Option[(Int, Double, Boolean, Boolean, Int, Option[java.io.File], () => AnyRef)] = {
    if (x$0 == null) None
    else Some.apply[(Int, Double, Boolean, Boolean, Int, Option[java.io.File], () => AnyRef)](
      Tuple7.apply[Int, Double, Boolean, Boolean, Int, Option[java.io.File], () => AnyRef](
        x$0.transitiveStep, x$0.recompileAllFraction, x$0.relationsDebug, x$0.apiDebug, x$0.apiDiffContextSize,
        x$0.apiDumpDirectory, x$0.newClassfileManager))
  }
  private def readResolve(): Object = IncOptions
  //- EXPANDED CASE CLASS METHOD END -//

  @deprecated("Use IncOptions.Default.withNewClassfileManager(ClassfileManager.transactional(tempDir)), instead.", "0.13.5")
  def defaultTransactional(tempDir: File): IncOptions =
    setTransactional(Default, tempDir)
  @deprecated("Use opts.withNewClassfileManager(ClassfileManager.transactional(tempDir)), instead.", "0.13.5")
  def setTransactional(opts: IncOptions, tempDir: File): IncOptions =
    opts.withNewClassfileManager(ClassfileManager.transactional(tempDir, sbt.Logger.Null))

  private val transitiveStepKey = "transitiveStep"
  private val recompileAllFractionKey = "recompileAllFraction"
  private val relationsDebugKey = "relationsDebug"
  private val apiDebugKey = "apiDebug"
  private val apiDumpDirectoryKey = "apiDumpDirectory"
  private val apiDiffContextSizeKey = "apiDiffContextSize"
  private val recompileOnMacroDefKey = "recompileOnMacroDef"
  private val nameHashingKey = "nameHashing"
  private val antStyleKey = "antStyle"

  def fromStringMap(m: java.util.Map[String, String]): IncOptions = {
    // all the code below doesn't look like idiomatic Scala for a good reason: we are working with Java API
    def getTransitiveStep: Int = {
      val k = transitiveStepKey
      if (m.containsKey(k)) m.get(k).toInt else Default.transitiveStep
    }
    def getRecompileAllFraction: Double = {
      val k = recompileAllFractionKey
      if (m.containsKey(k)) m.get(k).toDouble else Default.recompileAllFraction
    }
    def getRelationsDebug: Boolean = {
      val k = relationsDebugKey
      if (m.containsKey(k)) m.get(k).toBoolean else Default.relationsDebug
    }
    def getApiDebug: Boolean = {
      val k = apiDebugKey
      if (m.containsKey(k)) m.get(k).toBoolean else Default.apiDebug
    }
    def getApiDiffContextSize: Int = {
      val k = apiDiffContextSizeKey
      if (m.containsKey(k)) m.get(k).toInt else Default.apiDiffContextSize
    }
    def getApiDumpDirectory: Option[java.io.File] = {
      val k = apiDumpDirectoryKey
      if (m.containsKey(k))
        Some(new java.io.File(m.get(k)))
      else None
    }
    def getRecompileOnMacroDef: Boolean = {
      val k = recompileOnMacroDefKey
      if (m.containsKey(k)) m.get(k).toBoolean else Default.recompileOnMacroDef
    }
    def getNameHashing: Boolean = {
      val k = nameHashingKey
      if (m.containsKey(k)) m.get(k).toBoolean else Default.nameHashing
    }

    def getAntStyle: Boolean = {
      val k = antStyleKey
      if (m.containsKey(k)) m.get(k).toBoolean else Default.antStyle
    }

    new IncOptions(getTransitiveStep, getRecompileAllFraction, getRelationsDebug, getApiDebug, getApiDiffContextSize,
      getApiDumpDirectory, ClassfileManager.deleteImmediately, getRecompileOnMacroDef, getNameHashing, getAntStyle)
  }

  def toStringMap(o: IncOptions): java.util.Map[String, String] = {
    val m = new java.util.HashMap[String, String]
    m.put(transitiveStepKey, o.transitiveStep.toString)
    m.put(recompileAllFractionKey, o.recompileAllFraction.toString)
    m.put(relationsDebugKey, o.relationsDebug.toString)
    m.put(apiDebugKey, o.apiDebug.toString)
    o.apiDumpDirectory.foreach(f => m.put(apiDumpDirectoryKey, f.toString))
    m.put(apiDiffContextSizeKey, o.apiDiffContextSize.toString)
    m.put(recompileOnMacroDefKey, o.recompileOnMacroDef.toString)
    m.put(nameHashingKey, o.nameHashing.toString)
    m
  }
}
