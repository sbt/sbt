package sbt

object SourceWrapper {
  def accept(sources: Seq[sbt.internal.io.Source], file: File): Boolean =
    sources.exists(_.accept(file.toPath))
}
