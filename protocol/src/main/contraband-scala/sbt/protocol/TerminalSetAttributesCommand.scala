/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.protocol
final class TerminalSetAttributesCommand private (
  val iflag: String,
  val oflag: String,
  val cflag: String,
  val lflag: String,
  val cchars: String) extends sbt.protocol.CommandMessage() with Serializable {
  
  
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: TerminalSetAttributesCommand => (this.iflag == x.iflag) && (this.oflag == x.oflag) && (this.cflag == x.cflag) && (this.lflag == x.lflag) && (this.cchars == x.cchars)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (37 * (37 * (17 + "sbt.protocol.TerminalSetAttributesCommand".##) + iflag.##) + oflag.##) + cflag.##) + lflag.##) + cchars.##)
  }
  override def toString: String = {
    "TerminalSetAttributesCommand(" + iflag + ", " + oflag + ", " + cflag + ", " + lflag + ", " + cchars + ")"
  }
  private[this] def copy(iflag: String = iflag, oflag: String = oflag, cflag: String = cflag, lflag: String = lflag, cchars: String = cchars): TerminalSetAttributesCommand = {
    new TerminalSetAttributesCommand(iflag, oflag, cflag, lflag, cchars)
  }
  def withIflag(iflag: String): TerminalSetAttributesCommand = {
    copy(iflag = iflag)
  }
  def withOflag(oflag: String): TerminalSetAttributesCommand = {
    copy(oflag = oflag)
  }
  def withCflag(cflag: String): TerminalSetAttributesCommand = {
    copy(cflag = cflag)
  }
  def withLflag(lflag: String): TerminalSetAttributesCommand = {
    copy(lflag = lflag)
  }
  def withCchars(cchars: String): TerminalSetAttributesCommand = {
    copy(cchars = cchars)
  }
}
object TerminalSetAttributesCommand {
  
  def apply(iflag: String, oflag: String, cflag: String, lflag: String, cchars: String): TerminalSetAttributesCommand = new TerminalSetAttributesCommand(iflag, oflag, cflag, lflag, cchars)
}
