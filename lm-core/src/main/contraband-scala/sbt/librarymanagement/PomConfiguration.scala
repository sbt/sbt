/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.librarymanagement
final class PomConfiguration private (
  validate: Boolean,
  scalaModuleInfo: Option[sbt.librarymanagement.ScalaModuleInfo],
  val file: java.io.File,
  val autoScalaTools: Boolean) extends sbt.librarymanagement.ModuleSettings(validate, scalaModuleInfo) with Serializable {
  
  private def this(file: java.io.File, autoScalaTools: Boolean) = this(false, None, file, autoScalaTools)
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: PomConfiguration => (this.validate == x.validate) && (this.scalaModuleInfo == x.scalaModuleInfo) && (this.file == x.file) && (this.autoScalaTools == x.autoScalaTools)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (37 * (17 + "sbt.librarymanagement.PomConfiguration".##) + validate.##) + scalaModuleInfo.##) + file.##) + autoScalaTools.##)
  }
  override def toString: String = {
    "PomConfiguration(" + validate + ", " + scalaModuleInfo + ", " + file + ", " + autoScalaTools + ")"
  }
  private[this] def copy(validate: Boolean = validate, scalaModuleInfo: Option[sbt.librarymanagement.ScalaModuleInfo] = scalaModuleInfo, file: java.io.File = file, autoScalaTools: Boolean = autoScalaTools): PomConfiguration = {
    new PomConfiguration(validate, scalaModuleInfo, file, autoScalaTools)
  }
  def withValidate(validate: Boolean): PomConfiguration = {
    copy(validate = validate)
  }
  def withScalaModuleInfo(scalaModuleInfo: Option[sbt.librarymanagement.ScalaModuleInfo]): PomConfiguration = {
    copy(scalaModuleInfo = scalaModuleInfo)
  }
  def withScalaModuleInfo(scalaModuleInfo: sbt.librarymanagement.ScalaModuleInfo): PomConfiguration = {
    copy(scalaModuleInfo = Option(scalaModuleInfo))
  }
  def withFile(file: java.io.File): PomConfiguration = {
    copy(file = file)
  }
  def withAutoScalaTools(autoScalaTools: Boolean): PomConfiguration = {
    copy(autoScalaTools = autoScalaTools)
  }
}
object PomConfiguration {
  
  def apply(file: java.io.File, autoScalaTools: Boolean): PomConfiguration = new PomConfiguration(file, autoScalaTools)
  def apply(validate: Boolean, scalaModuleInfo: Option[sbt.librarymanagement.ScalaModuleInfo], file: java.io.File, autoScalaTools: Boolean): PomConfiguration = new PomConfiguration(validate, scalaModuleInfo, file, autoScalaTools)
  def apply(validate: Boolean, scalaModuleInfo: sbt.librarymanagement.ScalaModuleInfo, file: java.io.File, autoScalaTools: Boolean): PomConfiguration = new PomConfiguration(validate, Option(scalaModuleInfo), file, autoScalaTools)
}
