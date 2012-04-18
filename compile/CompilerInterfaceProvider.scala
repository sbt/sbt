package sbt
package compiler

	import java.io.File

trait CompilerInterfaceProvider
{
	def apply(scalaInstance: ScalaInstance, log: Logger): File
}
object CompilerInterfaceProvider
{
	def constant(file: File): CompilerInterfaceProvider = new CompilerInterfaceProvider {
		def apply(scalaInstance: ScalaInstance, log: Logger): File = file
	}
}