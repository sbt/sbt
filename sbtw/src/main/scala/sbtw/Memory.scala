package sbtw

object Memory:

  private val memoryOptPrefixes = Set(
    "-Xmx",
    "-Xms",
    "-Xss",
    "-XX:MaxPermSize",
    "-XX:MaxMetaspaceSize",
    "-XX:ReservedCodeCacheSize",
    "-XX:+UseCGroupMemoryLimitForHeap",
    "-XX:MaxRAM",
    "-XX:InitialRAMPercentage",
    "-XX:MaxRAMPercentage",
    "-XX:MinRAMPercentage"
  )

  def hasMemoryOpts(opts: Seq[String]): Boolean =
    opts.exists(o => memoryOptPrefixes.exists(p => o.startsWith(p)))

  def evictMemoryOpts(opts: Seq[String]): Seq[String] =
    opts.filter(o => !memoryOptPrefixes.exists(p => o.startsWith(p)))

  def addMemory(memMb: Int, javaVersion: Int): Seq[String] =
    var codecache = memMb / 8
    if codecache > 512 then codecache = 512
    if codecache < 128 then codecache = 128
    val classMetadataSize = codecache * 2
    val base = Seq(
      s"-Xms${memMb}m",
      s"-Xmx${memMb}m",
      "-Xss4M",
      s"-XX:ReservedCodeCacheSize=${codecache}m"
    )
    if javaVersion < 8 then base :+ s"-XX:MaxPermSize=${classMetadataSize}m"
    else base

  def addDefaultMemory(
      javaOpts: Seq[String],
      sbtOpts: Seq[String],
      javaVersion: Int,
      defaultMemMb: Int
  ): (Seq[String], Seq[String]) =
    val fromJava = hasMemoryOpts(javaOpts)
    val fromTool =
      sys.env.get("JAVA_TOOL_OPTIONS").exists(s => hasMemoryOpts(s.split("\\s+").toSeq))
    val fromJdk = sys.env.get("JDK_JAVA_OPTIONS").exists(s => hasMemoryOpts(s.split("\\s+").toSeq))
    val fromSbt = hasMemoryOpts(sbtOpts)
    if fromJava || fromTool || fromJdk || fromSbt then (javaOpts, sbtOpts)
    else
      val evictedJava = evictMemoryOpts(javaOpts)
      val evictedSbt = evictMemoryOpts(sbtOpts)
      val memOpts = addMemory(defaultMemMb, javaVersion)
      (evictedJava ++ memOpts, evictedSbt)
end Memory
