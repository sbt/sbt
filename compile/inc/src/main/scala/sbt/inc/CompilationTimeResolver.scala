package sbt
package inc

import java.io.File
import scala.collection.mutable.Map

private[inc] class CompilationTimeResolver(getAnalysis: String => Analysis) {

  private val cache: Map[File, Long] = Map.empty

  /**
   * Finds the time of a compilation more recent than `limit` that may have affected `file` (that is,
   * a compilation that included any transitive dependency of `file`). Stops as soon as such a compilation
   * is found. This information lets us know whether any of the transitive dependencies of `file` has been
   * recompiled since its last compilation.
   *
   * Internal and external transitive dependencies of `file` are checked.
   */
  def apply(currentAnalysis: Analysis, limit: Long, file: File): Long =
    cache get file match {
      case Some(result) => result

      case None =>
        val fileCompilationTime = currentAnalysis.apis.internalAPI(file).compilation.startTime
        // Immediately add this to the cache to avoid problems in case of cyclic dependencies
        cache += file -> fileCompilationTime

        if (fileCompilationTime > limit) {
          fileCompilationTime
        } else {
          val internalDeps = currentAnalysis.relations.internalSrcDeps(file).toStream
          val recompiledInternalDep = internalDeps map (this(currentAnalysis, limit, _)) find (_ > limit)

          recompiledInternalDep match {
            case Some(time) =>
              cache += file -> time
              time

            case None =>
              val externalDeps = currentAnalysis.relations.externalDeps(file).toStream
              val recompiledExternalDep =
                externalDeps map { dep =>
                  val analysis = getAnalysis(dep)
                  val correspondingFiles = analysis.relations.definesClass(dep) // This is typically only one entry
                  correspondingFiles.map(this(analysis, limit, _)).max
                } find (_ > limit)

              recompiledExternalDep match {
                case Some(time) =>
                  cache += file -> time
                  time

                case None =>
                  limit
              }
          }
        }
    }
}
