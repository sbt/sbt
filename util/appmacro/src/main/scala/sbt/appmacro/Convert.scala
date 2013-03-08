package sbt
package appmacro

	import scala.reflect._
	import macros._
	import Types.idFun

abstract class Convert
{
	def apply[T: c.WeakTypeTag](c: Context)(nme: String, in: c.Tree): Converted[c.type]
	def asPredicate(c: Context): (String, c.Type, c.Tree) => Boolean =
		(n,tpe,tree) => {
			val tag = c.WeakTypeTag(tpe)
			apply(c)(n,tree)(tag).isSuccess
		}
}
sealed trait Converted[C <: Context with Singleton] {
	def isSuccess: Boolean
	def transform(f: C#Tree => C#Tree): Converted[C]
}
object Converted {
	def NotApplicable[C <: Context with Singleton] = new NotApplicable[C]
	final case class Failure[C <: Context with Singleton](position: C#Position, message: String)  extends Converted[C] {
		def isSuccess = false
		def transform(f: C#Tree => C#Tree): Converted[C] = new Failure(position, message)
	}
	final class NotApplicable[C <: Context with Singleton] extends Converted[C] {
		def isSuccess = false
		def transform(f: C#Tree => C#Tree): Converted[C] = this
	}
	final case class Success[C <: Context with Singleton](tree: C#Tree, finalTransform: C#Tree => C#Tree) extends Converted[C] {
		def isSuccess = true
		def transform(f: C#Tree => C#Tree): Converted[C] = Success(f(tree), finalTransform)
	}
	object Success {
		def apply[C <: Context with Singleton](tree: C#Tree): Success[C] = Success(tree, idFun)
	}
}