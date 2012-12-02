package sbt
package std

	import Def.{Initialize,Setting}
	import Types.{idFun,Id}
	import TaskExtra.allM
	import appmacro.{ContextUtil, Convert, InputWrapper, Instance, MixedBuilder, MonadInstance}
	import complete.Parser

	import language.experimental.macros
	import scala.reflect._
	import reflect.macros._

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
	def apply[T: c.WeakTypeTag](c: Context)(in: c.Tree): c.Tree =
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
	final val AssignInitName = "set"
	final val Append1InitName = "append1"
	final val AppendNInitName = "appendN"
	final val TransformInitName = "transform"
	final val InputTaskCreateName = "create"

	def taskMacroImpl[T: c.WeakTypeTag](c: Context)(t: c.Expr[T]): c.Expr[Initialize[Task[T]]] = 
		Instance.contImpl[T](c, FullInstance, FullConvert, MixedBuilder)(Left(t))

	def taskDynMacroImpl[T: c.WeakTypeTag](c: Context)(t: c.Expr[Initialize[Task[T]]]): c.Expr[Initialize[Task[T]]] = 
		Instance.contImpl[T](c, FullInstance, FullConvert, MixedBuilder)(Right(t))

	/** Implementation of := macro for settings. */
	def settingAssignMacroImpl[T: c.WeakTypeTag](c: Context)(v: c.Expr[T]): c.Expr[Setting[T]] =
	{
		val init = SettingMacro.settingMacroImpl[T](c)(v)
		val assign = transformMacroImpl(c)( init.tree )( AssignInitName )
		c.Expr[Setting[T]]( assign )
	}
	/** Implementation of := macro for tasks. */
	def taskAssignMacroImpl[T: c.WeakTypeTag](c: Context)(v: c.Expr[T]): c.Expr[Setting[Task[T]]] =
	{
		val init = taskMacroImpl[T](c)(v)
		val assign = transformMacroImpl(c)( init.tree )( AssignInitName )
		c.Expr[Setting[Task[T]]]( assign )
	}

	/* Implementations of <<= macro variations for tasks and settings. These just get the source position of the call site.*/

	def itaskAssignPosition[T: c.WeakTypeTag](c: Context)(app: c.Expr[Initialize[Task[T]]]): c.Expr[Setting[Task[T]]] =
		settingAssignPosition(c)(app)
	def taskAssignPositionT[T: c.WeakTypeTag](c: Context)(app: c.Expr[Task[T]]): c.Expr[Setting[Task[T]]] =
		itaskAssignPosition(c)( c.universe.reify { Def.valueStrict(app.splice) })
	def taskAssignPositionPure[T: c.WeakTypeTag](c: Context)(app: c.Expr[T]): c.Expr[Setting[Task[T]]] =
		taskAssignPositionT(c)( c.universe.reify { TaskExtra.constant(app.splice) })

	def taskTransformPosition[S: c.WeakTypeTag](c: Context)(f: c.Expr[S => S]): c.Expr[Setting[Task[S]]] =
		c.Expr[Setting[Task[S]]]( transformMacroImpl(c)( f.tree )( TransformInitName ) )
	def settingTransformPosition[S: c.WeakTypeTag](c: Context)(f: c.Expr[S => S]): c.Expr[Setting[S]] =
		c.Expr[Setting[S]]( transformMacroImpl(c)( f.tree )( TransformInitName ) )

	def taskAppendNPosition[S: c.WeakTypeTag, V: c.WeakTypeTag](c: Context)(vs: c.Expr[Initialize[Task[V]]])(a: c.Expr[Append.Values[S, V]]): c.Expr[Setting[Task[S]]] =
		c.Expr[Setting[Task[S]]]( appendMacroImpl(c)( vs.tree, a.tree )( AppendNInitName ) )

	def settingAppendNPosition[S: c.WeakTypeTag, V: c.WeakTypeTag](c: Context)(vs: c.Expr[Initialize[V]])(a: c.Expr[Append.Values[S, V]]): c.Expr[Setting[S]] =
		c.Expr[Setting[S]]( appendMacroImpl(c)( vs.tree, a.tree )( AppendNInitName ) )

	def taskAppend1Position[S: c.WeakTypeTag, V: c.WeakTypeTag](c: Context)(v: c.Expr[Initialize[Task[V]]])(a: c.Expr[Append.Value[S, V]]): c.Expr[Setting[Task[S]]] =
		c.Expr[Setting[Task[S]]]( appendMacroImpl(c)( v.tree, a.tree )( Append1InitName ) )

	def settingAppend1Position[S: c.WeakTypeTag, V: c.WeakTypeTag](c: Context)(v: c.Expr[Initialize[V]])(a: c.Expr[Append.Value[S, V]]): c.Expr[Setting[S]] =
		c.Expr[Setting[S]]( appendMacroImpl(c)( v.tree, a.tree )( Append1InitName ) )

	def settingAssignPure[T: c.WeakTypeTag](c: Context)(app: c.Expr[T]): c.Expr[Setting[T]] =
		settingAssignPosition(c)( c.universe.reify { Def.valueStrict(app.splice) })
	def settingAssignPosition[T: c.WeakTypeTag](c: Context)(app: c.Expr[Initialize[T]]): c.Expr[Setting[T]] =
		c.Expr[Setting[T]]( transformMacroImpl(c)( app.tree )( AssignInitName ) )

	/** Implementation of := macro for tasks. */
	def inputTaskAssignMacroImpl[T: c.WeakTypeTag](c: Context)(v: c.Expr[T]): c.Expr[Setting[InputTask[T]]] =
	{
		val init = inputTaskMacroImpl[T](c)(v)
		val assign = transformMacroImpl(c)( init.tree )( AssignInitName )
		c.Expr[Setting[InputTask[T]]]( assign )
	}
	/** Implementation of += macro for tasks. */
	def taskAppend1Impl[T: c.WeakTypeTag, U: c.WeakTypeTag](c: Context)(v: c.Expr[U])(a: c.Expr[Append.Value[T, U]]): c.Expr[Setting[Task[T]]] =
	{
		val init = taskMacroImpl[U](c)(v)
		val assign = appendMacroImpl(c)( init.tree, a.tree )( Append1InitName )
		c.Expr[Setting[Task[T]]]( assign )
	}
	/** Implementation of += macro for settings. */
	def settingAppend1Impl[T: c.WeakTypeTag, U: c.WeakTypeTag](c: Context)(v: c.Expr[U])(a: c.Expr[Append.Value[T, U]]): c.Expr[Setting[T]] =
	{
		val init = SettingMacro.settingMacroImpl[U](c)(v)
		val assign = appendMacroImpl(c)( init.tree, a.tree )( Append1InitName )
		c.Expr[Setting[T]]( assign )
	}
	/** Implementation of ++= macro for tasks. */
	def taskAppendNImpl[T: c.WeakTypeTag, U: c.WeakTypeTag](c: Context)(vs: c.Expr[U])(a: c.Expr[Append.Values[T, U]]): c.Expr[Setting[Task[T]]] =
	{
		val init = taskMacroImpl[U](c)(vs)
		val assign = appendMacroImpl(c)( init.tree, a.tree )( AppendNInitName )
		c.Expr[Setting[Task[T]]]( assign )
	}
	/** Implementation of ++= macro for settings. */
	def settingAppendNImpl[T: c.WeakTypeTag, U: c.WeakTypeTag](c: Context)(vs: c.Expr[U])(a: c.Expr[Append.Values[T, U]]): c.Expr[Setting[T]] =
	{
		val init = SettingMacro.settingMacroImpl[U](c)(vs)
		val assign = appendMacroImpl(c)( init.tree, a.tree )( AppendNInitName )
		c.Expr[Setting[T]]( assign )
	}

	private[this] def appendMacroImpl(c: Context)(init: c.Tree, append: c.Tree)(newName: String): c.Tree =
	{
			import c.universe.{Apply,ApplyTag,newTermName,Select,SelectTag,TypeApply,TypeApplyTag}
		c.macroApplication match {
			case Apply(Apply(TypeApply(Select(preT, nmeT), targs), _), a) =>
				Apply(Apply(TypeApply(Select(preT, newTermName(newName).encodedName), targs), init :: sourcePosition(c).tree :: Nil), a)
			case x => ContextUtil.unexpectedTree(x)
		}
	}
	private[this] def transformMacroImpl(c: Context)(init: c.Tree)(newName: String): c.Tree =
	{
			import c.universe.{Apply,ApplyTag,newTermName,Select,SelectTag}
		val target = 
			c.macroApplication match {
				case Apply(Select(prefix, _), _) => prefix
				case x => ContextUtil.unexpectedTree(x)
			}
		Apply.apply(Select(target, newTermName(newName).encodedName), init :: sourcePosition(c).tree :: Nil)
	}
	private[this] def sourcePosition(c: Context): c.Expr[SourcePosition] =
	{
			import c.universe._
		val pos = c.enclosingPosition
		if(pos.isDefined && pos.line >= 0 && pos.source != null) {
			val f = pos.source.file
			val name = constant[String](c, settingSource(c, f.path, f.name))
			val line = constant[Int](c, pos.line)
			reify { sbt.LinePosition(name.splice, line.splice) }
		}
		else
			reify{ sbt.NoPosition }
	}
	private[this] def settingSource(c: Context, path: String, name: String): String =
	{
		val ec = c.enclosingClass.symbol
		def inEmptyPackage(s: c.Symbol): Boolean =
			s != c.universe.NoSymbol && (s.owner == c.mirror.EmptyPackage || s.owner == c.mirror.EmptyPackageClass || inEmptyPackage(s.owner))
		if(!ec.isStatic)
			name
		else if(inEmptyPackage(ec))
			path
		else
			s"(${ec.fullName}) $name"
	}

	private[this] def constant[T: c.TypeTag](c: Context, t: T): c.Expr[T] = {
		import c.universe._
		c.Expr[T](Literal(Constant(t)))
	}

	sealed abstract class MacroValue[T] {
		def value: T = macro std.TaskMacro.valueMacroImpl[T]
	}

	def valueMacroImpl[T: c.WeakTypeTag](c: Context): c.Expr[T] =
		ContextUtil.selectMacroImpl[T,Any](c)( ts => InputWrapper.wrapKey[T](c)(ts) )

	sealed abstract class RawParserInput[T] {
		def parsed: T = macro std.TaskMacro.rawParserMacro[T]
	}
	sealed abstract class InitParserInput[T] {
		def parsed: T = macro std.TaskMacro.initParserMacro[T]
	}
	sealed abstract class StateParserInput[T] {
		def parsed: T = macro std.TaskMacro.stateParserMacro[T]
	}

	/** Implements `Parser[T].parsed` by wrapping the Parser with the ParserInput wrapper.*/
	def rawParserMacro[T: c.WeakTypeTag](c: Context): c.Expr[T] =
		ContextUtil.selectMacroImpl[T, Parser[T]](c) { p => c.universe.reify {
			ParserInput.parser_\u2603\u2603[T](InputTask.parserAsInput(p.splice))
		}}

	/** Implements the `Initialize[Parser[T]].parsed` macro by wrapping the input with the ParserInput wrapper.*/
	def initParserMacro[T: c.WeakTypeTag](c: Context): c.Expr[T] =
		ContextUtil.selectMacroImpl[T, Initialize[Parser[T]]](c) { t => c.universe.reify {
			ParserInput.parser_\u2603\u2603[T](InputTask.initParserAsInput(t.splice))
		}}

	/** Implements the `Initialize[State => Parser[T]].parsed` macro by wrapping the input with the ParserInput wrapper.*/
	def stateParserMacro[T: c.WeakTypeTag](c: Context): c.Expr[T] =
		ContextUtil.selectMacroImpl[T, Initialize[State => Parser[T]]](c) { t => c.universe.reify {
			ParserInput.parser_\u2603\u2603[T](t.splice)
		}}

	/** Implementation detail.  The method temporarily holds the input parser (as a Tree, at compile time) until the input task macro processes it. */
	object ParserInput {
		/** The name of the wrapper method should be obscure.
		* Wrapper checking is based solely on this name, so it must not conflict with a user method name.
		* The user should never see this method because it is compile-time only and only used internally by the task macros.*/
		val WrapName = "parser_\u2603\u2603"

		// This method should be annotated as compile-time only when that feature is implemented
		def parser_\u2603\u2603[T](i: Initialize[State => Parser[T]]): T = error("This method is an implementation detail and should not be referenced.")
	}

	def inputTaskMacroImpl[T: c.WeakTypeTag](c: Context)(t: c.Expr[T]): c.Expr[Initialize[InputTask[T]]] =
		inputTaskMacro0[T](c)(Left(t))
	def inputTaskDynMacroImpl[T: c.WeakTypeTag](c: Context)(t: c.Expr[Initialize[Task[T]]]): c.Expr[Initialize[InputTask[T]]] =
		inputTaskMacro0[T](c)(Right(t))

	private[this] def inputTaskMacro0[T: c.WeakTypeTag](c: Context)(t: Either[c.Expr[T], c.Expr[Initialize[Task[T]]]]): c.Expr[Initialize[InputTask[T]]] =	
	{
			import c.universe.{Apply=>ApplyTree,_}
		
		val tag = implicitly[c.WeakTypeTag[T]]
		val util = ContextUtil[c.type](c)
		val it = Ident(util.singleton(InputTask))
		val isParserWrapper = util.isWrapper(ParserInput.WrapName)
		val isTaskWrapper = util.isWrapper(InputWrapper.WrapName)
		val isAnyWrapper = (tr: Tree) => isParserWrapper(tr) || isTaskWrapper(tr)
		val ttree = t match { case Left(l) => l.tree; case Right(r) => r.tree }
		val defs = util.collectDefs(ttree, isAnyWrapper)
		val checkQual = util.checkReferences(defs, isAnyWrapper)
		val unitTask = c.typeOf[TaskKey[Unit]]
		val taskKeyC = unitTask.typeConstructor

		var result: Option[(Tree, Type, ValDef)] = None

		def subWrapper(tpe: Type, qual: Tree): Tree =
			if(result.isDefined)
			{
				c.error(qual.pos, "An InputTask can only have a single input parser.")
				EmptyTree
			}
			else
			{
				qual.foreach(checkQual)
				val keyType = appliedType(taskKeyC, tpe :: Nil) // TaskKey[<tpe>]
				val vd = util.freshValDef(keyType, qual.symbol) // val $x: TaskKey[<tpe>]
				result = Some( (qual, tpe, vd) )
				val tree = util.refVal(vd) // $x
				tree.setPos(qual.pos) // position needs to be set so that wrapKey passes the position onto the wrapper
				assert(tree.tpe != null, "Null type: " + tree)
				val wrapped = InputWrapper.wrapKey(c)( c.Expr[Any](tree) )( c.WeakTypeTag(tpe) )
				wrapped.tree.setType(tpe)
			}
		// Tree for InputTask.create[<tpeA>, <tpeB>](arg1)(arg2)
		def inputTaskApply(tpeA: Type, tpeB: Type, arg1: Tree, arg2: Tree) =
		{
			val typedApp = TypeApply(Select(it, InputTaskCreateName), TypeTree(tpeA) :: TypeTree(tpeB) :: Nil)
			val app = ApplyTree( ApplyTree(typedApp, arg1 :: Nil), arg2 :: Nil)
			c.Expr[Initialize[InputTask[T]]](app)
		}
		def expandTask(dyn: Boolean, tx: Tree): c.Expr[Initialize[Task[T]]] =
			if(dyn)
				taskDynMacroImpl[T](c)( c.Expr[Initialize[Task[T]]](tx) )
			else
				taskMacroImpl[T](c)( c.Expr[T](tx) )

		val tx = util.transformWrappers(ttree, isParserWrapper, (tpe,tree) => subWrapper(tpe,tree))
		val body = c.resetLocalAttrs( expandTask(t.isRight, tx).tree )
		result match {
			case Some((p, tpe, param)) =>
				val f = Function(param :: Nil, body)
				inputTaskApply(tpe, tag.tpe, p, f)
			case None =>
				// SI-6591 prevents the more direct version using reify:
				// reify { InputTask[Unit,T].create(TaskMacro.emptyParser)(Types.const(body.splice)) }
				val initType = c.weakTypeOf[Initialize[Task[T]]]
				val tt = Ident(util.singleton(Types))
				val f = ApplyTree(TypeApply(Select(tt, "const"), TypeTree(unitTask) :: TypeTree(initType) :: Nil), body :: Nil)
				val p = reify { InputTask.emptyParser }
				inputTaskApply(c.typeOf[Unit], tag.tpe, p.tree, f)
		}
	}
}


/** Convert instance for plain `Task`s not within the settings system.
* This is not used for the main task/setting macros, but could be used when manipulating plain Tasks.*/
object TaskConvert extends Convert
{
	def apply[T: c.WeakTypeTag](c: Context)(in: c.Tree): c.Tree =
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
	def taskImpl[T: c.WeakTypeTag](c: Context)(t: c.Expr[T]): c.Expr[Task[T]] = 
		Instance.contImpl[T](c, TaskInstance, TaskConvert, MixedBuilder)(Left(t))

	def taskDyn[T](t: Task[T]): Task[T] = macro taskDynImpl[T]
	def taskDynImpl[T: c.WeakTypeTag](c: Context)(t: c.Expr[Task[T]]): c.Expr[Task[T]] = 
		Instance.contImpl[T](c, TaskInstance, TaskConvert, MixedBuilder)(Right(t))
}
