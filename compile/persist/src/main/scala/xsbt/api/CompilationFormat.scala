package xsbt.api

import xsbti.api._
import sbinary._

object CompilationFormat extends Format[Compilation] {
  import java.io._
  def reads(in: Input): Compilation = {
    val oin = new ObjectInputStream(new InputWrapperStream(in))
    try { oin.readObject.asInstanceOf[Compilation] } finally { oin.close() }
  }
  def writes(out: Output, src: Compilation): Unit = {
    val oout = new ObjectOutputStream(new OutputWrapperStream(out))
    try { oout.writeObject(src) } finally { oout.close() }
  }
}
