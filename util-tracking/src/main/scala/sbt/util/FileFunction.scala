package sbt.util

import java.io.File

object FileFunction {
  type UpdateFunction = (ChangeReport[File], ChangeReport[File]) => Set[File]
  private val defaultInStyle = FileInfo.lastModified
  private val defaultOutStyle = FileInfo.exists

  /**
   * Generic change-detection helper used to help build / artifact generation /
   * etc. steps detect whether or not they need to run. Returns a function whose
   * input is a Set of input files, and subsequently executes the action function
   * (which does the actual work: compiles, generates resources, etc.), returning
   * a Set of output files that it generated.
   *
   * The input file and resulting output file state is cached in stores issued by
   * `storeFactory`. On each invocation, the state of the input and output
   * files from the previous run is compared against the cache, as is the set of
   * input files. If a change in file state / input files set is detected, the
   * action function is re-executed.
   *
   * @param cacheBaseDirectory The folder in which to store
   * @param action The work function, which receives a list of input files and returns a list of output files
   */
  def cached(cacheBaseDirectory: File)(action: Set[File] => Set[File]): Set[File] => Set[File] =
    cached(cacheBaseDirectory, inStyle = defaultInStyle, outStyle = defaultOutStyle)(action)

  /**
   * Generic change-detection helper used to help build / artifact generation /
   * etc. steps detect whether or not they need to run. Returns a function whose
   * input is a Set of input files, and subsequently executes the action function
   * (which does the actual work: compiles, generates resources, etc.), returning
   * a Set of output files that it generated.
   *
   * The input file and resulting output file state is cached in stores issued by
   * `storeFactory`. On each invocation, the state of the input and output
   * files from the previous run is compared against the cache, as is the set of
   * input files. If a change in file state / input files set is detected, the
   * action function is re-executed.
   *
   * @param cacheBaseDirectory The folder in which to store
   * @param inStyle The strategy by which to detect state change in the input files from the previous run
   * @param action The work function, which receives a list of input files and returns a list of output files
   */
  def cached(cacheBaseDirectory: File, inStyle: FileInfo.Style)(action: Set[File] => Set[File]): Set[File] => Set[File] =
    cached(cacheBaseDirectory, inStyle = inStyle, outStyle = defaultOutStyle)(action)

  /**
   * Generic change-detection helper used to help build / artifact generation /
   * etc. steps detect whether or not they need to run. Returns a function whose
   * input is a Set of input files, and subsequently executes the action function
   * (which does the actual work: compiles, generates resources, etc.), returning
   * a Set of output files that it generated.
   *
   * The input file and resulting output file state is cached in stores issued by
   * `storeFactory`. On each invocation, the state of the input and output
   * files from the previous run is compared against the cache, as is the set of
   * input files. If a change in file state / input files set is detected, the
   * action function is re-executed.
   *
   * @param cacheBaseDirectory The folder in which to store
   * @param inStyle The strategy by which to detect state change in the input files from the previous run
   * @param outStyle The strategy by which to detect state change in the output files from the previous run
   * @param action The work function, which receives a list of input files and returns a list of output files
   */
  def cached(cacheBaseDirectory: File, inStyle: FileInfo.Style, outStyle: FileInfo.Style)(action: Set[File] => Set[File]): Set[File] => Set[File] =
    cached(CacheStoreFactory(cacheBaseDirectory), inStyle, outStyle)((in, out) => action(in.checked))

  /**
   * Generic change-detection helper used to help build / artifact generation /
   * etc. steps detect whether or not they need to run. Returns a function whose
   * input is a Set of input files, and subsequently executes the action function
   * (which does the actual work: compiles, generates resources, etc.), returning
   * a Set of output files that it generated.
   *
   * The input file and resulting output file state is cached in stores issued by
   * `storeFactory`. On each invocation, the state of the input and output
   * files from the previous run is compared against the cache, as is the set of
   * input files. If a change in file state / input files set is detected, the
   * action function is re-executed.
   *
   * @param storeFactory The factory to use to get stores for the input and output files.
   * @param action The work function, which receives a list of input files and returns a list of output files
   */
  def cached(storeFactory: CacheStoreFactory)(action: UpdateFunction): Set[File] => Set[File] =
    cached(storeFactory, inStyle = defaultInStyle, outStyle = defaultOutStyle)(action)

  /**
   * Generic change-detection helper used to help build / artifact generation /
   * etc. steps detect whether or not they need to run. Returns a function whose
   * input is a Set of input files, and subsequently executes the action function
   * (which does the actual work: compiles, generates resources, etc.), returning
   * a Set of output files that it generated.
   *
   * The input file and resulting output file state is cached in stores issued by
   * `storeFactory`. On each invocation, the state of the input and output
   * files from the previous run is compared against the cache, as is the set of
   * input files. If a change in file state / input files set is detected, the
   * action function is re-executed.
   *
   * @param storeFactory The factory to use to get stores for the input and output files.
   * @param inStyle The strategy by which to detect state change in the input files from the previous run
   * @param action The work function, which receives a list of input files and returns a list of output files
   */
  def cached(storeFactory: CacheStoreFactory, inStyle: FileInfo.Style)(action: UpdateFunction): Set[File] => Set[File] =
    cached(storeFactory, inStyle = inStyle, outStyle = defaultOutStyle)(action)

  /**
   * Generic change-detection helper used to help build / artifact generation /
   * etc. steps detect whether or not they need to run. Returns a function whose
   * input is a Set of input files, and subsequently executes the action function
   * (which does the actual work: compiles, generates resources, etc.), returning
   * a Set of output files that it generated.
   *
   * The input file and resulting output file state is cached in stores issued by
   * `storeFactory`. On each invocation, the state of the input and output
   * files from the previous run is compared against the cache, as is the set of
   * input files. If a change in file state / input files set is detected, the
   * action function is re-executed.
   *
   * @param storeFactory The factory to use to get stores for the input and output files.
   * @param inStyle The strategy by which to detect state change in the input files from the previous run
   * @param outStyle The strategy by which to detect state change in the output files from the previous run
   * @param action The work function, which receives a list of input files and returns a list of output files
   */
  def cached(storeFactory: CacheStoreFactory, inStyle: FileInfo.Style, outStyle: FileInfo.Style)(action: UpdateFunction): Set[File] => Set[File] =
    {
      lazy val inCache = Difference.inputs(storeFactory.make("in-cache"), inStyle)
      lazy val outCache = Difference.outputs(storeFactory.make("out-cache"), outStyle)
      inputs =>
        {
          inCache(inputs) { inReport =>
            outCache { outReport =>
              if (inReport.modified.isEmpty && outReport.modified.isEmpty)
                outReport.checked
              else
                action(inReport, outReport)
            }
          }
        }
    }
}
