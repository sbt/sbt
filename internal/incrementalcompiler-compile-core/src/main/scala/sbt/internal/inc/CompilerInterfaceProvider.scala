package sbt
package internal
package inc

import java.io.File
import sbt.util.Logger

trait CompilerInterfaceProvider {
  def apply(scalaInstance: xsbti.compile.ScalaInstance, log: Logger): File
}
object CompilerInterfaceProvider {
  def constant(file: File): CompilerInterfaceProvider = new CompilerInterfaceProvider {
    def apply(scalaInstance: xsbti.compile.ScalaInstance, log: Logger): File = file
  }
}
