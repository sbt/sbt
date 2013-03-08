package sbt
package std

	import language.experimental.macros
	import scala.reflect._
	import reflect.macros._
	import reflect.internal.annotations.compileTimeOnly

	import Def.Initialize
	import appmacro.ContextUtil
	import complete.Parser

/** Implementation detail.  The wrap methods temporarily hold inputs (as a Tree, at compile time) until a task or setting macro processes it. */
object InputWrapper
{
	/* The names of the wrapper methods should be obscure.
	* Wrapper checking is based solely on this name, so it must not conflict with a user method name.
	* The user should never see this method because it is compile-time only and only used internally by the task macro system.*/

	private[std] final val WrapTaskName = "wrapTask_\u2603\u2603"
	private[std] final val WrapInitName = "wrapInit_\u2603\u2603"
	private[std] final val WrapInitTaskName = "wrapInitTask_\u2603\u2603"
	private[std] final val WrapInitInputName = "wrapInitInputTask_\u2603\u2603"
	private[std] final val WrapInputName = "wrapInputTask_\u2603\u2603"

	@compileTimeOnly("`value` can only be called on a task within a task definition macro, such as :=, +=, ++=, or Def.task.")
	def wrapTask_\u2603\u2603[T](in: Any): T = implDetailError

	@compileTimeOnly("`value` can only be used within a task or setting macro, such as :=, +=, ++=, Def.task, or Def.setting.")
	def wrapInit_\u2603\u2603[T](in: Any): T = implDetailError

	@compileTimeOnly("`value` can only be called on a task within a task definition macro, such as :=, +=, ++=, or Def.task.")
	def wrapInitTask_\u2603\u2603[T](in: Any): T = implDetailError

	@compileTimeOnly("`value` can only be called on an input task within a task definition macro, such as := or Def.inputTask.")
	def wrapInputTask_\u2603\u2603[T](in: Any): T = implDetailError

	@compileTimeOnly("`value` can only be called on an input task within a task definition macro, such as := or Def.inputTask.")
	def wrapInitInputTask_\u2603\u2603[T](in: Any): T = implDetailError

	private[this] def implDetailError = error("This method is an implementation detail and should not be referenced.")

	private[std] def wrapTask[T: c.WeakTypeTag](c: Context)(ts: c.Expr[Any], pos: c.Position): c.Expr[T] =
		wrapImpl[T,InputWrapper.type](c, InputWrapper, WrapTaskName)(ts, pos)
	private[std] def wrapInit[T: c.WeakTypeTag](c: Context)(ts: c.Expr[Any], pos: c.Position): c.Expr[T] =
		wrapImpl[T,InputWrapper.type](c, InputWrapper, WrapInitName)(ts, pos)
	private[std] def wrapInitTask[T: c.WeakTypeTag](c: Context)(ts: c.Expr[Any], pos: c.Position): c.Expr[T] =
		wrapImpl[T,InputWrapper.type](c, InputWrapper, WrapInitTaskName)(ts, pos)

	private[std] def wrapInitInputTask[T: c.WeakTypeTag](c: Context)(ts: c.Expr[Any], pos: c.Position): c.Expr[T] =
		wrapImpl[T,InputWrapper.type](c, InputWrapper, WrapInitInputName)(ts, pos)
	private[std] def wrapInputTask[T: c.WeakTypeTag](c: Context)(ts: c.Expr[Any], pos: c.Position): c.Expr[T] =
		wrapImpl[T,InputWrapper.type](c, InputWrapper, WrapInputName)(ts, pos)

	/** Wraps an arbitrary Tree in a call to the `<s>.<wrapName>` method of this module for later processing by an enclosing macro.
	* The resulting Tree is the manually constructed version of:
	*
	* `c.universe.reify { <s>.<wrapName>[T](ts.splice) }`
	*/
	def wrapImpl[T: c.WeakTypeTag, S <: AnyRef with Singleton](c: Context, s: S, wrapName: String)(ts: c.Expr[Any], pos: c.Position)(implicit it: c.TypeTag[s.type]): c.Expr[T] =
	{
			import c.universe.{Apply=>ApplyTree,_}
		val util = new ContextUtil[c.type](c)
		val iw = util.singleton(s)
		val tpe = c.weakTypeOf[T]
		val nme = newTermName(wrapName).encoded
		val sel = Select(Ident(iw), nme)
		sel.setPos(pos) // need to set the position on Select, because that is where the compileTimeOnly check looks
		val tree = ApplyTree(TypeApply(sel, TypeTree(tpe) :: Nil), ts.tree :: Nil)
		tree.setPos(ts.tree.pos)
		c.Expr[T](tree)
	}

	def valueMacroImpl[T: c.WeakTypeTag](c: Context): c.Expr[T] =
		ContextUtil.selectMacroImpl[T](c) { (ts,pos) =>
			val tpe = ts.tree.tpe
			if(tpe <:< c.weakTypeOf[Initialize[Task[T]]])
				InputWrapper.wrapInitTask[T](c)(ts,pos)
			else if(tpe <:< c.weakTypeOf[Initialize[T]])
				InputWrapper.wrapInit[T](c)(ts,pos)
			else if(tpe <:< c.weakTypeOf[Task[T]])
				InputWrapper.wrapTask[T](c)(ts,pos)
			else if(tpe <:< c.weakTypeOf[InputTask[T]])
				InputWrapper.wrapInputTask[T](c)(ts,pos)
			else if(tpe <:< c.weakTypeOf[Initialize[InputTask[T]]])
				InputWrapper.wrapInitInputTask[T](c)(ts,pos)

			else
				c.abort(pos, s"Internal sbt error. Unexpected type $tpe")
		}
}

sealed abstract class MacroValue[T] {
	@compileTimeOnly("`value` can only be used within a task or setting macro, such as :=, +=, ++=, Def.task, or Def.setting.")
	def value: T = macro InputWrapper.valueMacroImpl[T]
}
sealed abstract class ParserInput[T] {
	@compileTimeOnly("`parsed` can only be used within an input task macro, such as := or Def.inputTask.")
	def parsed: T = macro ParserInput.parsedMacroImpl[T]
}
sealed abstract class ParserInputTask[T] {
	@compileTimeOnly("`parsed` can only be used within an input task macro, such as := or Def.inputTask.")
	def parsed: Task[T] = macro ParserInput.parsedInputMacroImpl[T]
}

/** Implementation detail.  The wrap method temporarily holds the input parser (as a Tree, at compile time) until the input task macro processes it. */
object ParserInput {
	/* The name of the wrapper method should be obscure.
	* Wrapper checking is based solely on this name, so it must not conflict with a user method name.
	* The user should never see this method because it is compile-time only and only used internally by the task macros.*/
	private[std] val WrapName = "parser_\u2603\u2603"
	private[std] val WrapInitName = "initParser_\u2603\u2603"

	@compileTimeOnly("`parsed` can only be used within an input task macro, such as := or Def.inputTask.")
	def parser_\u2603\u2603[T](i: Any): T = error("This method is an implementation detail and should not be referenced.")

	@compileTimeOnly("`parsed` can only be used within an input task macro, such as := or Def.inputTask.")
	def initParser_\u2603\u2603[T](i: Any): T = error("This method is an implementation detail and should not be referenced.")

	private[std] def wrap[T: c.WeakTypeTag](c: Context)(ts: c.Expr[Any], pos: c.Position): c.Expr[T] =
		InputWrapper.wrapImpl[T,ParserInput.type](c, ParserInput, WrapName)(ts, pos)
	private[std] def wrapInit[T: c.WeakTypeTag](c: Context)(ts: c.Expr[Any], pos: c.Position): c.Expr[T] =
		InputWrapper.wrapImpl[T,ParserInput.type](c, ParserInput, WrapInitName)(ts, pos)

	private[std] def inputParser[T: c.WeakTypeTag](c: Context)(t: c.Expr[InputTask[T]]): c.Expr[State => Parser[Task[T]]] = c.universe.reify(t.splice.parser)

	def parsedInputMacroImpl[T: c.WeakTypeTag](c: Context): c.Expr[Task[T]] =
		ContextUtil.selectMacroImpl[Task[T]](c) { (p,pos) =>
			import c.universe.reify
			val tpe = p.tree.tpe
			if(tpe <:< c.weakTypeOf[InputTask[T]]) {
				val e = c.Expr[InputTask[T]](p.tree)
				wrap[Task[T]](c)( inputParser(c)(e), pos )
			}
			else if(tpe <:< c.weakTypeOf[Initialize[InputTask[T]]]) {
				val e = c.Expr[Initialize[InputTask[T]]](p.tree)
				wrapInit[Task[T]](c)( reify { Def.toIParser(e.splice) }, pos )
			}
			else
				c.abort(pos, s"Internal sbt error. Unexpected type ${tpe.normalize} in parsedInputMacroImpl.")
		}

	/** Implements `Parser[T].parsed` by wrapping the Parser with the ParserInput wrapper.*/
	def parsedMacroImpl[T: c.WeakTypeTag](c: Context): c.Expr[T] =
		ContextUtil.selectMacroImpl[T](c) { (p,pos) =>
			import c.universe.reify
			val tpe = p.tree.tpe
			if(tpe <:< c.weakTypeOf[Parser[T]]) {
				val e = c.Expr[Parser[T]](p.tree)
				wrap[T](c)( reify { Def.toSParser(e.splice) }, pos)
			}
			else if(tpe <:< c.weakTypeOf[State => Parser[T]])
				wrap[T](c)( p, pos)
			else if(tpe <:< c.weakTypeOf[Initialize[Parser[T]]]) {
				val e = c.Expr[Initialize[Parser[T]]](p.tree)
				val es = reify { Def.toISParser(e.splice) }
				wrapInit[T](c)(es, pos)
			}
			else if(tpe <:< c.weakTypeOf[Initialize[State => Parser[T]]])
				wrapInit[T](c)(p,pos)
			else
				c.abort(pos, s"Internal sbt error. Unexpected type ${tpe.normalize} in parsedMacroImpl")
		}
}
