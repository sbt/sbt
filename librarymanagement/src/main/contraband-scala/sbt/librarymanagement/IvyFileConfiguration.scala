/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.librarymanagement
final class IvyFileConfiguration private (
  validate: Boolean,
  ivyScala: Option[sbt.librarymanagement.IvyScala],
  val file: java.io.File,
  val autoScalaTools: Boolean) extends sbt.librarymanagement.ModuleSettings(validate, ivyScala) with Serializable {
  
  
  
  override def equals(o: Any): Boolean = o match {
    case x: IvyFileConfiguration => (this.validate == x.validate) && (this.ivyScala == x.ivyScala) && (this.file == x.file) && (this.autoScalaTools == x.autoScalaTools)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (37 * (17 + "IvyFileConfiguration".##) + validate.##) + ivyScala.##) + file.##) + autoScalaTools.##)
  }
  override def toString: String = {
    "IvyFileConfiguration(" + validate + ", " + ivyScala + ", " + file + ", " + autoScalaTools + ")"
  }
  protected[this] def copy(validate: Boolean = validate, ivyScala: Option[sbt.librarymanagement.IvyScala] = ivyScala, file: java.io.File = file, autoScalaTools: Boolean = autoScalaTools): IvyFileConfiguration = {
    new IvyFileConfiguration(validate, ivyScala, file, autoScalaTools)
  }
  def withValidate(validate: Boolean): IvyFileConfiguration = {
    copy(validate = validate)
  }
  def withIvyScala(ivyScala: Option[sbt.librarymanagement.IvyScala]): IvyFileConfiguration = {
    copy(ivyScala = ivyScala)
  }
  def withIvyScala(ivyScala: sbt.librarymanagement.IvyScala): IvyFileConfiguration = {
    copy(ivyScala = Option(ivyScala))
  }
  def withFile(file: java.io.File): IvyFileConfiguration = {
    copy(file = file)
  }
  def withAutoScalaTools(autoScalaTools: Boolean): IvyFileConfiguration = {
    copy(autoScalaTools = autoScalaTools)
  }
}
object IvyFileConfiguration {
  
  def apply(validate: Boolean, ivyScala: Option[sbt.librarymanagement.IvyScala], file: java.io.File, autoScalaTools: Boolean): IvyFileConfiguration = new IvyFileConfiguration(validate, ivyScala, file, autoScalaTools)
  def apply(validate: Boolean, ivyScala: sbt.librarymanagement.IvyScala, file: java.io.File, autoScalaTools: Boolean): IvyFileConfiguration = new IvyFileConfiguration(validate, Option(ivyScala), file, autoScalaTools)
}
