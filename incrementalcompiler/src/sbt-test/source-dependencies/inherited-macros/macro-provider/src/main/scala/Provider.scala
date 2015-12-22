package macro
import scala.language.experimental.macros
import scala.reflect.macros._

abstract class Provider {
	def notImplementedMacro = macro ???
}
