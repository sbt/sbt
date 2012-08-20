
package sbt
package std

	import Def.{Initialize,Setting}
	import Types.{idFun,Id}
	import TaskExtra.allM
	import appmacro.{Convert, InputWrapper, Instance, MixedBuilder, MonadInstance}

	import language.experimental.macros
	import scala.reflect._
	import makro._

/** Instance for the monad/applicative functor for plain Tasks. */
object TaskInstance extends MonadInstance
{
	import TaskExtra._

	final type M[x] = Task[x]
	def app[K[L[x]], Z](in: K[Task], f: K[Id] => Z)(implicit a: AList[K]): Task[Z] = new Mapped[Z,K](in, f compose allM, a)
	def map[S,T](in: Task[S], f: S => T): Task[T] = in map f
	def flatten[T](in: Task[Task[T]]): Task[T] = in flatMap idFun[Task[T]]
	def pure[T](t: () => T): Task[T] = toTask(t)
}

/** Composes the Task and Initialize Instances to provide an Instance for [T] Initialize[Task[T]].*/
object FullInstance extends Instance.Composed[Initialize, Task](InitializeInstance, TaskInstance) with MonadInstance
{
	type SS = sbt.Settings[Scope]
	val settingsData = TaskKey[SS]("settings-data", "Provides access to the project data for the build.", KeyRanks.DTask)

	def flatten[T](in: Initialize[Task[Initialize[Task[T]]]]): Initialize[Task[T]] =
	{
			import Scoped._
		(in,settingsData, Def.capturedTransformations) apply{
			(a: Task[Initialize[Task[T]]], data: Task[SS], f) =>
				import TaskExtra.multT2Task
				(a, data) flatMap { case (a,d) => f(a) evaluate d }
		}
	}
}
/** Converts an input `Tree` of type `Initialize[T]`, `Initialize[Task[T]]`, or `Task[T]` into a `Tree` of type `Initialize[Task[T]]`.*/
object FullConvert extends Convert
{
	def apply[T: c.AbsTypeTag](c: Context)(in: c.Tree): c.Tree =
	{
		val util = appmacro.ContextUtil[c.type](c)
		if(in.tpe <:< util.atypeOf[Initialize[Task[T]]])
			in
		else if(in.tpe <:< util.atypeOf[Initialize[T]])
		{
			val i = c.Expr[Initialize[T]](in)
			c.universe.reify( Def.toITask(i.splice) ).tree
		}
		else if(in.tpe <:< util.atypeOf[Task[T]])
		{
			val i = c.Expr[Task[T]](in)
			c.universe.reify( Def.valueStrict[Task[T]](i.splice) ).tree
		}
		else
			c.abort(in.pos, "Unknown input type: " + in.tpe)
	}
}

object TaskMacro
{
	final val AssignInitName = "<<="
	final val Append1InitName = "<+="
	final val AppendNInitName = "<++="

	def taskMacroImpl[T: c.AbsTypeTag](c: Context)(t: c.Expr[T]): c.Expr[Initialize[Task[T]]] = 
		Instance.contImpl[T](c, FullInstance, FullConvert, MixedBuilder)(Left(t))

	def taskDynMacroImpl[T: c.AbsTypeTag](c: Context)(t: c.Expr[Initialize[Task[T]]]): c.Expr[Initialize[Task[T]]] = 
		Instance.contImpl[T](c, FullInstance, FullConvert, MixedBuilder)(Right(t))

	/** Implementation of := macro for settings. */
	def settingAssignMacroImpl[T: c.AbsTypeTag](c: Context)(v: c.Expr[T]): c.Expr[Setting[T]] =
	{
		val init = SettingMacro.settingMacroImpl[T](c)(v)
		val assign = transformMacroImpl(c)( init.tree )( AssignInitName )
		c.Expr[Setting[T]]( assign )
	}
	/** Implementation of := macro for tasks. */
	def taskAssignMacroImpl[T: c.AbsTypeTag](c: Context)(v: c.Expr[T]): c.Expr[Setting[Task[T]]] =
	{
		val init = taskMacroImpl[T](c)(v)
		val assign = transformMacroImpl(c)( init.tree )( AssignInitName )
		c.Expr[Setting[Task[T]]]( assign )
	}
	/** Implementation of += macro for tasks. */
	def taskAppend1Impl[T: c.AbsTypeTag, U: c.AbsTypeTag](c: Context)(v: c.Expr[U])(a: c.Expr[Append.Value[T, U]]): c.Expr[Setting[Task[T]]] =
	{
		val init = taskMacroImpl[U](c)(v)
		val assign = appendMacroImpl(c)( init.tree, a.tree )( Append1InitName )
		c.Expr[Setting[Task[T]]]( assign )
	}
	/** Implementation of += macro for settings. */
	def settingAppend1Impl[T: c.AbsTypeTag, U: c.AbsTypeTag](c: Context)(v: c.Expr[U])(a: c.Expr[Append.Value[T, U]]): c.Expr[Setting[T]] =
	{
		val init = SettingMacro.settingMacroImpl[U](c)(v)
		val assign = appendMacroImpl(c)( init.tree, a.tree )( Append1InitName )
		c.Expr[Setting[T]]( assign )
	}
	/** Implementation of ++= macro for tasks. */
	def taskAppendNImpl[T: c.AbsTypeTag, U: c.AbsTypeTag](c: Context)(vs: c.Expr[U])(a: c.Expr[Append.Values[T, U]]): c.Expr[Setting[Task[T]]] =
	{
		val init = taskMacroImpl[U](c)(vs)
		val assign = appendMacroImpl(c)( init.tree, a.tree )( AppendNInitName )
		c.Expr[Setting[Task[T]]]( assign )
	}
	/** Implementation of ++= macro for settings. */
	def settingAppendNImpl[T: c.AbsTypeTag, U: c.AbsTypeTag](c: Context)(vs: c.Expr[U])(a: c.Expr[Append.Values[T, U]]): c.Expr[Setting[T]] =
	{
		val init = SettingMacro.settingMacroImpl[U](c)(vs)
		val assign = appendMacroImpl(c)( init.tree, a.tree )( AppendNInitName )
		c.Expr[Setting[T]]( assign )
	}

	private[this] def appendMacroImpl(c: Context)(init: c.Tree, append: c.Tree)(newName: String): c.Tree =
	{
			import c.universe.{Apply,newTermName,Select,TypeApply}
		c.macroApplication match {
			case Apply(Apply(TypeApply(Select(preT, nmeT), targs), _), a) =>	
				Apply(Apply(TypeApply(Select(preT, newTermName(newName).encodedName), targs), init :: Nil), a)
			case x => unexpectedTree(x)
		}
	}
	private[this] def transformMacroImpl(c: Context)(init: c.Tree)(newName: String): c.Tree =
	{
			import c.universe.{Apply,newTermName,Select}
		val target = 
			c.macroApplication match {
				case Apply(Select(prefix, _), _) => prefix
				case x => unexpectedTree(x)
			}
		Apply.apply(Select(target, newTermName(newName).encodedName), init :: Nil)
	}
	private[this] def unexpectedTree[C <: Context](tree: C#Tree): Nothing = error("Unexpected macro application tree (" + tree.getClass + "): " + tree)

	sealed abstract class MacroValue[T] {
		def value: T = macro std.TaskMacro.valueMacroImpl[T]
	}

	def valueMacroImpl[T: c.AbsTypeTag](c: Context): c.Expr[T] =
	{
			import c.universe._
		c.macroApplication match {
			case Select(Apply(_, t :: Nil), _) => wrap[T](c)(t)
			case x => unexpectedTree(x)
		}
	}
	private[this] def wrap[T: c.AbsTypeTag](c: Context)(t: c.Tree): c.Expr[T] =
	{
		val ts = c.Expr[Any](t)
		c.universe.reify { InputWrapper.wrap[T](ts.splice) }
	}
}

/** Convert instance for plain `Task`s not within the settings system.
* This is not used for the main task/setting macros, but could be used when manipulating plain Tasks.*/
object TaskConvert extends Convert
{
	def apply[T: c.AbsTypeTag](c: Context)(in: c.Tree): c.Tree =
	{
		val u = appmacro.ContextUtil[c.type](c)
		if(in.tpe <:< u.atypeOf[Task[T]])
			in
		else
			c.abort(in.pos, "Unknown input type: " + in.tpe)
	}
}

object PlainTaskMacro
{
	def task[T](t: T): Task[T] = macro taskImpl[T]
	def taskImpl[T: c.AbsTypeTag](c: Context)(t: c.Expr[T]): c.Expr[Task[T]] = 
		Instance.contImpl[T](c, TaskInstance, TaskConvert, MixedBuilder)(Left(t))

	def taskDyn[T](t: Task[T]): Task[T] = macro taskDynImpl[T]
	def taskDynImpl[T: c.AbsTypeTag](c: Context)(t: c.Expr[Task[T]]): c.Expr[Task[T]] = 
		Instance.contImpl[T](c, TaskInstance, TaskConvert, MixedBuilder)(Right(t))
}
