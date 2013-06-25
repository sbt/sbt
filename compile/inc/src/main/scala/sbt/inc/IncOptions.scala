package sbt.inc

	import java.io.File

/**
 * Represents all configuration options for the incremental compiler itself and
 * not the underlying Java/Scala compiler.
 */
final case class IncOptions(
	/** After which step include whole transitive closure of invalidated source files. */
	transitiveStep: Int,
	/**
	* What's the fraction of invalidated source files when we switch to recompiling
	* all files and giving up incremental compilation altogether. That's useful in
	* cases when probability that we end up recompiling most of source files but
	* in multiple steps is high. Multi-step incremental recompilation is slower
	* than recompiling everything in one step.
	*/
	recompileAllFraction: Double,
	/** Print very detailed information about relations, such as dependencies between source files. */
	relationsDebug: Boolean,
	/**
	* Enable tools for debugging API changes. At the moment this option is unused but in the
	* future it will enable for example:
	*   - disabling API hashing and API minimization (potentially very memory consuming)
	*   - diffing textual API representation which helps understanding what kind of changes
	*     to APIs are visible to the incremental compiler
	*/
	apiDebug: Boolean,
	/**
	 * Controls context size (in lines) displayed when diffs are produced for textual API
	 * representation.
	 *
	 * This option is used only when `apiDebug == true`.
	 */
	apiDiffContextSize: Int,
	/**
	* The directory where we dump textual representation of APIs. This method might be called
	* only if apiDebug returns true. This is unused option at the moment as the needed functionality
	* is not implemented yet.
	*/
	apiDumpDirectory: Option[java.io.File],
	/** Creates a new ClassfileManager that will handle class file deletion and addition during a single incremental compilation run. */
	newClassfileManager: () => ClassfileManager
)

object IncOptions {
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
		newClassfileManager = ClassfileManager.deleteImmediately
	)
	def defaultTransactional(tempDir: File): IncOptions = setTransactional(Default, tempDir)
	def setTransactional(opts: IncOptions, tempDir: File): IncOptions =
		opts.copy(newClassfileManager = ClassfileManager.transactional(tempDir))

	val transitiveStepKey       = "transitiveStep"
	val recompileAllFractionKey = "recompileAllFraction"
	val relationsDebugKey       = "relationsDebug"
	val apiDebugKey             = "apiDebug"
	val apiDumpDirectoryKey     = "apiDumpDirectory"
	val apiDiffContextSize      = "apiDiffContextSize"

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
			val k = apiDiffContextSize
			if (m.containsKey(k)) m.get(k).toInt else Default.apiDiffContextSize
		}
		def getApiDumpDirectory: Option[java.io.File] = {
			val k = apiDumpDirectoryKey
			if (m.containsKey(k))
				Some(new java.io.File(m.get(k)))
			else None
		}

		IncOptions(getTransitiveStep, getRecompileAllFraction, getRelationsDebug, getApiDebug, getApiDiffContextSize,
			getApiDumpDirectory, ClassfileManager.deleteImmediately)
	}

	def toStringMap(o: IncOptions): java.util.Map[String, String] = {
		val m = new java.util.HashMap[String, String]
		m.put(transitiveStepKey, o.transitiveStep.toString)
		m.put(recompileAllFractionKey, o.recompileAllFraction.toString)
		m.put(relationsDebugKey, o.relationsDebug.toString)
		m.put(apiDebugKey, o.apiDebug.toString)
		o.apiDumpDirectory.foreach(f => m.put(apiDumpDirectoryKey, f.toString))
		m.put(apiDiffContextSize, o.apiDiffContextSize.toString)
		m
	}
}
