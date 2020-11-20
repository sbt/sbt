/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.protocol
final class TerminalAttributesResponse private (
  val iflag: String,
  val oflag: String,
  val cflag: String,
  val lflag: String,
  val cchars: String) extends sbt.protocol.EventMessage() with Serializable {
  
  
  
  override def equals(o: Any): Boolean = o match {
    case x: TerminalAttributesResponse => (this.iflag == x.iflag) && (this.oflag == x.oflag) && (this.cflag == x.cflag) && (this.lflag == x.lflag) && (this.cchars == x.cchars)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (37 * (37 * (17 + "sbt.protocol.TerminalAttributesResponse".##) + iflag.##) + oflag.##) + cflag.##) + lflag.##) + cchars.##)
  }
  override def toString: String = {
    "TerminalAttributesResponse(" + iflag + ", " + oflag + ", " + cflag + ", " + lflag + ", " + cchars + ")"
  }
  private[this] def copy(iflag: String = iflag, oflag: String = oflag, cflag: String = cflag, lflag: String = lflag, cchars: String = cchars): TerminalAttributesResponse = {
    new TerminalAttributesResponse(iflag, oflag, cflag, lflag, cchars)
  }
  def withIflag(iflag: String): TerminalAttributesResponse = {
    copy(iflag = iflag)
  }
  def withOflag(oflag: String): TerminalAttributesResponse = {
    copy(oflag = oflag)
  }
  def withCflag(cflag: String): TerminalAttributesResponse = {
    copy(cflag = cflag)
  }
  def withLflag(lflag: String): TerminalAttributesResponse = {
    copy(lflag = lflag)
  }
  def withCchars(cchars: String): TerminalAttributesResponse = {
    copy(cchars = cchars)
  }
}
object TerminalAttributesResponse {
  
  def apply(iflag: String, oflag: String, cflag: String, lflag: String, cchars: String): TerminalAttributesResponse = new TerminalAttributesResponse(iflag, oflag, cflag, lflag, cchars)
}
