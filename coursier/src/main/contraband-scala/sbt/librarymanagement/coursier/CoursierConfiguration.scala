/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.librarymanagement.coursier
final class CoursierConfiguration private (
  val log: Option[xsbti.Logger],
  val resolvers: Vector[sbt.librarymanagement.Resolver],
  val otherResolvers: Vector[sbt.librarymanagement.Resolver],
  val reorderResolvers: Boolean,
  val parallelDownloads: Int,
  val maxIterations: Int) extends Serializable {
  
  private def this() = this(None, sbt.librarymanagement.Resolver.defaults, Vector.empty, true, 6, 100)
  
  override def equals(o: Any): Boolean = o match {
    case x: CoursierConfiguration => (this.log == x.log) && (this.resolvers == x.resolvers) && (this.otherResolvers == x.otherResolvers) && (this.reorderResolvers == x.reorderResolvers) && (this.parallelDownloads == x.parallelDownloads) && (this.maxIterations == x.maxIterations)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (37 * (37 * (37 * (17 + "sbt.librarymanagement.coursier.CoursierConfiguration".##) + log.##) + resolvers.##) + otherResolvers.##) + reorderResolvers.##) + parallelDownloads.##) + maxIterations.##)
  }
  override def toString: String = {
    "CoursierConfiguration(" + log + ", " + resolvers + ", " + otherResolvers + ", " + reorderResolvers + ", " + parallelDownloads + ", " + maxIterations + ")"
  }
  private[this] def copy(log: Option[xsbti.Logger] = log, resolvers: Vector[sbt.librarymanagement.Resolver] = resolvers, otherResolvers: Vector[sbt.librarymanagement.Resolver] = otherResolvers, reorderResolvers: Boolean = reorderResolvers, parallelDownloads: Int = parallelDownloads, maxIterations: Int = maxIterations): CoursierConfiguration = {
    new CoursierConfiguration(log, resolvers, otherResolvers, reorderResolvers, parallelDownloads, maxIterations)
  }
  def withLog(log: Option[xsbti.Logger]): CoursierConfiguration = {
    copy(log = log)
  }
  def withLog(log: xsbti.Logger): CoursierConfiguration = {
    copy(log = Option(log))
  }
  def withResolvers(resolvers: Vector[sbt.librarymanagement.Resolver]): CoursierConfiguration = {
    copy(resolvers = resolvers)
  }
  def withOtherResolvers(otherResolvers: Vector[sbt.librarymanagement.Resolver]): CoursierConfiguration = {
    copy(otherResolvers = otherResolvers)
  }
  def withReorderResolvers(reorderResolvers: Boolean): CoursierConfiguration = {
    copy(reorderResolvers = reorderResolvers)
  }
  def withParallelDownloads(parallelDownloads: Int): CoursierConfiguration = {
    copy(parallelDownloads = parallelDownloads)
  }
  def withMaxIterations(maxIterations: Int): CoursierConfiguration = {
    copy(maxIterations = maxIterations)
  }
}
object CoursierConfiguration {
  
  def apply(): CoursierConfiguration = new CoursierConfiguration()
  def apply(log: Option[xsbti.Logger], resolvers: Vector[sbt.librarymanagement.Resolver], otherResolvers: Vector[sbt.librarymanagement.Resolver], reorderResolvers: Boolean, parallelDownloads: Int, maxIterations: Int): CoursierConfiguration = new CoursierConfiguration(log, resolvers, otherResolvers, reorderResolvers, parallelDownloads, maxIterations)
  def apply(log: xsbti.Logger, resolvers: Vector[sbt.librarymanagement.Resolver], otherResolvers: Vector[sbt.librarymanagement.Resolver], reorderResolvers: Boolean, parallelDownloads: Int, maxIterations: Int): CoursierConfiguration = new CoursierConfiguration(Option(log), resolvers, otherResolvers, reorderResolvers, parallelDownloads, maxIterations)
}
