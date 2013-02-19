package sbt.inc

/**
 * Case class that represents all configuration options for incremental compiler.
 *
 * Those are options that configure incremental compiler itself and not underlying
 * Java/Scala compiler.
 */
case class IncOptions(
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
    /** Print very detail information about relations (like dependencies between source files). */
    val relationsDebug: Boolean,
    /**
     * Enable tools for debugging API changes. At the moment that option is unused but in the
     * future it will enable for example:
     *   - disabling API hashing and API minimization (potentially very memory consuming)
     *   - dumping textual API representation into files
     */
    val apiDebug: Boolean,
    /**
     * The directory where we dump textual representation of APIs. This method might be called
     * only if apiDebug returns true. This is unused option at the moment as the needed functionality
     * is not implemented yet.
     */
    val apiDumpDirectory: Option[java.io.File])

object IncOptions {
	val Default = IncOptions(
		transitiveStep = 2,
		recompileAllFraction = 0.5,
		relationsDebug = false,
		apiDebug = false,
		apiDumpDirectory = None)

	val transitiveStepKey       = "transitiveStep"
	val recompileAllFractionKey = "recompileAllFraction"
	val relationsDebugKey       = "relationsDebug"
	val apiDebugKey             = "apiDebug"
	val apiDumpDirectoryKey     = "apiDumpDirectory"

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
		def getApiDumpDirectory: Option[java.io.File] = {
			val k = apiDumpDirectoryKey
			if (m.containsKey(k))
				Some(new java.io.File(m.get(k)))
			else None
		}

		IncOptions(getTransitiveStep, getRecompileAllFraction, getRelationsDebug, getApiDebug, getApiDumpDirectory)
	}

	def toStringMap(o: IncOptions): java.util.Map[String, String] = {
		val m = new java.util.HashMap[String, String]
		m.put(transitiveStepKey, o.transitiveStep.toString)
		m.put(recompileAllFractionKey, o.recompileAllFraction.toString)
		m.put(relationsDebugKey, o.relationsDebug.toString)
		m.put(apiDebugKey, o.apiDebug.toString)
		o.apiDumpDirectory.foreach(f => m.put(apiDumpDirectoryKey, f.toString))
		m
	}
}
