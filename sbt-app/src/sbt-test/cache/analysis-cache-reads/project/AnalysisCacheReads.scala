// In package sbt so that the read counts, which are sbt-private, are reachable from this build.
package sbt

object AnalysisCacheReads:
  @volatile private var mark: (Long, Long, Long) = (0L, 0L, 0L)

  def record(): Unit = mark = sbt.internal.BuildDef.AnalysisCacheStats.reads

  def since(): (Long, Long, Long) =
    val (served, hashed, deserialized) = sbt.internal.BuildDef.AnalysisCacheStats.reads
    (served - mark._1, hashed - mark._2, deserialized - mark._3)
