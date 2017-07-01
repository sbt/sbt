/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.librarymanagement
final class PomConfiguration private (
  validate: Boolean,
  ivyScala: Option[sbt.librarymanagement.IvyScala],
  val file: java.io.File,
  val autoScalaTools: Boolean) extends sbt.librarymanagement.ModuleSettings(validate, ivyScala) with Serializable {
  
  
  
  override def equals(o: Any): Boolean = o match {
    case x: PomConfiguration => (this.validate == x.validate) && (this.ivyScala == x.ivyScala) && (this.file == x.file) && (this.autoScalaTools == x.autoScalaTools)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (37 * (17 + "PomConfiguration".##) + validate.##) + ivyScala.##) + file.##) + autoScalaTools.##)
  }
  override def toString: String = {
    "PomConfiguration(" + validate + ", " + ivyScala + ", " + file + ", " + autoScalaTools + ")"
  }
  protected[this] def copy(validate: Boolean = validate, ivyScala: Option[sbt.librarymanagement.IvyScala] = ivyScala, file: java.io.File = file, autoScalaTools: Boolean = autoScalaTools): PomConfiguration = {
    new PomConfiguration(validate, ivyScala, file, autoScalaTools)
  }
  def withValidate(validate: Boolean): PomConfiguration = {
    copy(validate = validate)
  }
  def withIvyScala(ivyScala: Option[sbt.librarymanagement.IvyScala]): PomConfiguration = {
    copy(ivyScala = ivyScala)
  }
  def withIvyScala(ivyScala: sbt.librarymanagement.IvyScala): PomConfiguration = {
    copy(ivyScala = Option(ivyScala))
  }
  def withFile(file: java.io.File): PomConfiguration = {
    copy(file = file)
  }
  def withAutoScalaTools(autoScalaTools: Boolean): PomConfiguration = {
    copy(autoScalaTools = autoScalaTools)
  }
}
object PomConfiguration {
  
  def apply(validate: Boolean, ivyScala: Option[sbt.librarymanagement.IvyScala], file: java.io.File, autoScalaTools: Boolean): PomConfiguration = new PomConfiguration(validate, ivyScala, file, autoScalaTools)
  def apply(validate: Boolean, ivyScala: sbt.librarymanagement.IvyScala, file: java.io.File, autoScalaTools: Boolean): PomConfiguration = new PomConfiguration(validate, Option(ivyScala), file, autoScalaTools)
}
