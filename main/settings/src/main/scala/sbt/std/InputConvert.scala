package sbt
package std

	import language.experimental.macros
	import scala.reflect._
	import reflect.macros._

	import Def.Initialize
	import complete.Parser
	import appmacro.{Convert, Converted}


object InputInitConvert extends Convert
{
	def apply[T: c.WeakTypeTag](c: Context)(nme: String, in: c.Tree): Converted[c.type] =
		if(nme == InputWrapper.WrapInitName)
			Converted.Success(in)
		else if(nme == InputWrapper.WrapInitTaskName)
			Converted.Failure(in.pos, "Internal sbt error: initialize+task wrapper not split")
		else
			Converted.NotApplicable
}

/** Converts an input `Tree` of type `Parser[T]` or `State => Parser[T]` into a `Tree` of type `State => Parser[T]`.*/
object ParserConvert extends Convert
{
	def apply[T: c.WeakTypeTag](c: Context)(nme: String, in: c.Tree): Converted[c.type] =
	{
		if(nme == ParserInput.WrapName)
			Converted.Success(in)
		else if(nme == ParserInput.WrapInitName)
			Converted.Failure(in.pos, "Internal sbt error: initialize+parser wrapper not split")
		else
			Converted.NotApplicable
	}
}

/** Convert instance for plain `Task`s not within the settings system. */
object TaskConvert extends Convert
{
	def apply[T: c.WeakTypeTag](c: Context)(nme: String, in: c.Tree): Converted[c.type] =
		if(nme == InputWrapper.WrapTaskName)
			Converted.Success(in)
		else
			Converted.NotApplicable
}

/** Converts an input `Tree` of type `Initialize[T]`, `Initialize[Task[T]]`, or `Task[T]` into a `Tree` of type `Initialize[Task[T]]`.*/
object FullConvert extends Convert
{
		import InputWrapper._
	def apply[T: c.WeakTypeTag](c: Context)(nme: String, in: c.Tree): Converted[c.type] =
		if(nme == WrapInitTaskName)
			Converted.Success(in)
		else if(nme == WrapInitName)
		{
			val i = c.Expr[Initialize[T]](in)
			val t = c.universe.reify( Def.toITask(i.splice) ).tree
			Converted.Success(t)
		}
		else if(nme == WrapTaskName)
		{
			val i = c.Expr[Task[T]](in)
			val t = c.universe.reify( Def.valueStrict[Task[T]](i.splice) ).tree
			Converted.Success(t)
		}
		else
			Converted.NotApplicable
}

/** Converts an input `Tree` of type `State => Parser[T]` or `Initialize[State => Parser[T]]`
* into a `Tree` of type `Initialize[State => Parser[T]]`.*/
object InitParserConvert extends Convert
{
	def apply[T: c.WeakTypeTag](c: Context)(nme: String, in: c.Tree): Converted[c.type] =
		if(nme == ParserInput.WrapName) {
			val e = c.Expr[State => Parser[T]](in)
			val t = c.universe.reify { Def.valueStrict[State => Parser[T]](e.splice) }
			Converted.Success(t.tree)
		}
		else if(nme == ParserInput.WrapInitName)
			Converted.Success(in)
		else
			Converted.NotApplicable
}

