/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.bsp
/**
 * @param name Module name
 * @param version Module version
 * @param dataKind Kind of data to expect in the `data` field.
 * @param data Language-specific metadata about this module.
 */
final class DependencyModule private (
  val name: String,
  val version: String,
  val dataKind: Option[String],
  val data: Option[sjsonnew.shaded.scalajson.ast.unsafe.JValue]) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: DependencyModule => (this.name == x.name) && (this.version == x.version) && (this.dataKind == x.dataKind) && (this.data == x.data)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (37 * (17 + "sbt.internal.bsp.DependencyModule".##) + name.##) + version.##) + dataKind.##) + data.##)
  }
  override def toString: String = {
    "DependencyModule(" + name + ", " + version + ", " + dataKind + ", " + data + ")"
  }
  private def copy(name: String = name, version: String = version, dataKind: Option[String] = dataKind, data: Option[sjsonnew.shaded.scalajson.ast.unsafe.JValue] = data): DependencyModule = {
    new DependencyModule(name, version, dataKind, data)
  }
  def withName(name: String): DependencyModule = {
    copy(name = name)
  }
  def withVersion(version: String): DependencyModule = {
    copy(version = version)
  }
  def withDataKind(dataKind: Option[String]): DependencyModule = {
    copy(dataKind = dataKind)
  }
  def withDataKind(dataKind: String): DependencyModule = {
    copy(dataKind = Option(dataKind))
  }
  def withData(data: Option[sjsonnew.shaded.scalajson.ast.unsafe.JValue]): DependencyModule = {
    copy(data = data)
  }
  def withData(data: sjsonnew.shaded.scalajson.ast.unsafe.JValue): DependencyModule = {
    copy(data = Option(data))
  }
}
object DependencyModule {
  
  def apply(name: String, version: String, dataKind: Option[String], data: Option[sjsonnew.shaded.scalajson.ast.unsafe.JValue]): DependencyModule = new DependencyModule(name, version, dataKind, data)
  def apply(name: String, version: String, dataKind: String, data: sjsonnew.shaded.scalajson.ast.unsafe.JValue): DependencyModule = new DependencyModule(name, version, Option(dataKind), Option(data))
}
