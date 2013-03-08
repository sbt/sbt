package sbt
package std

	import Def.{Initialize,Setting}
	import Types.{const, idFun,Id}
	import TaskExtra.allM
	import appmacro.{ContextUtil, Convert, Converted, Instance, MixedBuilder, MonadInstance}
	import Instance.Transform
	import complete.{DefaultParsers,Parser}

	import language.experimental.macros
	import scala.reflect._
	import reflect.macros._
	import reflect.internal.annotations.compileTimeOnly

/** Instance for the monad/applicative functor for plain Tasks. */
object TaskInstance extends MonadInstance
{
	import TaskExtra._

	final type M[x] = Task[x]
	def app[K[L[x]], Z](in: K[Task], f: K[Id] => Z)(implicit a: AList[K]): Task[Z] = Task(Info(), new Mapped[Z,K](in, f compose allM, a))
	def map[S,T](in: Task[S], f: S => T): Task[T] = in map f
	def flatten[T](in: Task[Task[T]]): Task[T] = in flatMap idFun[Task[T]]
	def pure[T](t: () => T): Task[T] = toTask(t)
}
object ParserInstance extends Instance
{
		import sbt.Classes.Applicative
	private[this] implicit val parserApplicative: Applicative[M] = new Applicative[M]{
		def apply[S,T](f: M[S => T], v: M[S]): M[T] = s => (f(s) ~ v(s)) map { case (a,b) => a(b) }
		def pure[S](s: => S) = const(Parser.success(s))
		def map[S, T](f: S => T, v: M[S]) = s => v(s).map(f)
	}

	final type M[x] = State => Parser[x]
	def app[K[L[x]], Z](in: K[M], f: K[Id] => Z)(implicit a: AList[K]): M[Z] = a.apply(in,f)
	def map[S,T](in: M[S], f: S => T): M[T] = s => in(s) map f
	def pure[T](t: () => T): State => Parser[T] = const(DefaultParsers.success(t()))
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
	def flattenFun[S,T](in: Initialize[Task[ S => Initialize[Task[T]] ]]): Initialize[S => Task[T]] =
	{
			import Scoped._
		(in,settingsData, Def.capturedTransformations) apply{
			(a: Task[S => Initialize[Task[T]]], data: Task[SS], f) => (s: S) =>
				import TaskExtra.multT2Task
				(a, data) flatMap { case (af,d) => f(af(s)) evaluate d }
		}
	}
}

object TaskMacro
{
	final val AssignInitName = "set"
	final val Append1InitName = "append1"
	final val AppendNInitName = "appendN"
	final val TransformInitName = "transform"
	final val InputTaskCreateDynName = "createDyn"
	final val InputTaskCreateFreeName = "createFree"

	def taskMacroImpl[T: c.WeakTypeTag](c: Context)(t: c.Expr[T]): c.Expr[Initialize[Task[T]]] = 
		Instance.contImpl[T,Id](c, FullInstance, FullConvert, MixedBuilder)(Left(t), Instance.idTransform[c.type])

	def taskDynMacroImpl[T: c.WeakTypeTag](c: Context)(t: c.Expr[Initialize[Task[T]]]): c.Expr[Initialize[Task[T]]] = 
		Instance.contImpl[T,Id](c, FullInstance, FullConvert, MixedBuilder)(Right(t), Instance.idTransform[c.type])

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
	def itaskTransformPosition[S: c.WeakTypeTag](c: Context)(f: c.Expr[S => S]): c.Expr[Setting[S]] =
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


	def inputTaskMacroImpl[T: c.WeakTypeTag](c: Context)(t: c.Expr[T]): c.Expr[Initialize[InputTask[T]]] =
		inputTaskMacro0[T](c)(t)
	def inputTaskDynMacroImpl[T: c.WeakTypeTag](c: Context)(t: c.Expr[Initialize[Task[T]]]): c.Expr[Initialize[InputTask[T]]] =
		inputTaskDynMacro0[T](c)(t)

	private[this] def inputTaskMacro0[T: c.WeakTypeTag](c: Context)(t: c.Expr[T]): c.Expr[Initialize[InputTask[T]]] =
		iInitializeMacro(c)(t) { et =>
			val pt = iParserMacro(c)(et) { pt => 
				iTaskMacro(c)(pt)
			}
			c.universe.reify { InputTask.make(pt.splice) }
		}

	private[this] def iInitializeMacro[M[_], T](c: Context)(t: c.Expr[T])(f: c.Expr[T] => c.Expr[M[T]])(implicit tt: c.WeakTypeTag[T], mt: c.WeakTypeTag[M[T]]): c.Expr[Initialize[M[T]]] =
	{
		val inner: Transform[c.type,M] = new Transform[c.type,M] { def apply(in: c.Tree): c.Tree = f(c.Expr[T](in)).tree }
		val cond = c.Expr[T](conditionInputTaskTree(c)(t.tree))
		Instance.contImpl[T,M](c, InitializeInstance, InputInitConvert, MixedBuilder)(Left(cond), inner)
	}
	private[this] def conditionInputTaskTree(c: Context)(t: c.Tree): c.Tree =
	{
			import c.universe._
			import InputWrapper._
		def wrapInitTask[T: c.WeakTypeTag](tree: Tree) =
		{
			val e = c.Expr[Initialize[Task[T]]](tree)
			wrapTask[T](c)( wrapInit[Task[T]](c)(e, tree.pos), tree.pos).tree
		}
		def wrapInitParser[T: c.WeakTypeTag](tree: Tree) =
		{
			val e = c.Expr[Initialize[State => Parser[T]]](tree)
			ParserInput.wrap[T](c)( wrapInit[State => Parser[T]](c)(e, tree.pos), tree.pos).tree
		}
		def wrapInitInput[T: c.WeakTypeTag](tree: Tree) =
		{
			val e = c.Expr[Initialize[InputTask[T]]](tree)
			wrapInput[T]( wrapInit[InputTask[T]](c)(e, tree.pos).tree )
		}
		def wrapInput[T: c.WeakTypeTag](tree: Tree) =
		{
			val e = c.Expr[InputTask[T]](tree)
			val p = ParserInput.wrap[Task[T]](c)( ParserInput.inputParser(c)(e), tree.pos )
			wrapTask[T](c)(p, tree.pos).tree
		}

		def expand(nme: String, tpe: Type, tree: Tree): Converted[c.type] = nme match {
			case WrapInitTaskName => Converted.Success(wrapInitTask(tree)(c.WeakTypeTag(tpe)))
			case ParserInput.WrapInitName => Converted.Success(wrapInitParser(tree)(c.WeakTypeTag(tpe)))
			case WrapInitInputName => Converted.Success(wrapInitInput(tree)(c.WeakTypeTag(tpe)))
			case WrapInputName => Converted.Success(wrapInput(tree)(c.WeakTypeTag(tpe)))
			case _ => Converted.NotApplicable
		}
		val util = ContextUtil[c.type](c)
		util.transformWrappers(t, (nme,tpe,tree) => expand(nme,tpe,tree))
	}
		
	private[this] def iParserMacro[M[_], T](c: Context)(t: c.Expr[T])(f: c.Expr[T] => c.Expr[M[T]])(implicit tt: c.WeakTypeTag[T], mt: c.WeakTypeTag[M[T]]): c.Expr[State => Parser[M[T]]] =
	{
		val inner: Transform[c.type,M] = new Transform[c.type,M] { def apply(in: c.Tree): c.Tree = f(c.Expr[T](in)).tree }
		Instance.contImpl[T,M](c, ParserInstance, ParserConvert, MixedBuilder)(Left(t), inner)
	}
		
	private[this] def iTaskMacro[T: c.WeakTypeTag](c: Context)(t: c.Expr[T]): c.Expr[Task[T]] =
		Instance.contImpl[T,Id](c, TaskInstance, TaskConvert, MixedBuilder)(Left(t), Instance.idTransform)

	private[this] def inputTaskDynMacro0[T: c.WeakTypeTag](c: Context)(t: c.Expr[Initialize[Task[T]]]): c.Expr[Initialize[InputTask[T]]] =	
	{
			import c.universe.{Apply=>ApplyTree,_}
		
		val tag = implicitly[c.WeakTypeTag[T]]
		val util = ContextUtil[c.type](c)
		val it = Ident(util.singleton(InputTask))
		val isParserWrapper = InitParserConvert.asPredicate(c)
		val isTaskWrapper = FullConvert.asPredicate(c)
		val isAnyWrapper = (n: String, tpe: Type, tr: Tree) => isParserWrapper(n,tpe,tr) || isTaskWrapper(n,tpe,tr)
		val ttree = t.tree
		val defs = util.collectDefs(ttree, isAnyWrapper)
		val checkQual = util.checkReferences(defs, isAnyWrapper)

		var result: Option[(Tree, Type, ValDef)] = None

		def subWrapper(tpe: Type, qual: Tree): Tree =
			if(result.isDefined)
			{
				c.error(qual.pos, "Implementation restriction: a dynamic InputTask can only have a single input parser.")
				EmptyTree
			}
			else
			{
				qual.foreach(checkQual)
				val vd = util.freshValDef(tpe, qual.symbol) // val $x: <tpe>
				result = Some( (qual, tpe, vd) )
				val tree = util.refVal(vd) // $x
				tree.setPos(qual.pos) // position needs to be set so that wrapKey passes the position onto the wrapper
				assert(tree.tpe != null, "Null type: " + tree)
				tree.setType(tpe)
				tree
			}
		// Tree for InputTask.<name>[<tpeA>, <tpeB>](arg1)(arg2)
		def inputTaskCreate(name: String, tpeA: Type, tpeB: Type, arg1: Tree, arg2: Tree) =
		{
			val typedApp = TypeApply(util.select(it, name), TypeTree(tpeA) :: TypeTree(tpeB) :: Nil)
			val app = ApplyTree( ApplyTree(typedApp, arg1 :: Nil), arg2 :: Nil)
			c.Expr[Initialize[InputTask[T]]](app)
		}
		// Tree for InputTask.createFree[<tpe>](arg1)
		def inputTaskCreateFree(tpe: Type, arg: Tree) =
		{
			val typedApp = TypeApply(util.select(it, InputTaskCreateFreeName), TypeTree(tpe) :: Nil)
			val app = ApplyTree(typedApp, arg :: Nil)
			c.Expr[Initialize[InputTask[T]]](app)
		}
		def expandTask[I: WeakTypeTag](dyn: Boolean, tx: Tree): c.Expr[Initialize[Task[I]]] =
			if(dyn)
				taskDynMacroImpl[I](c)( c.Expr[Initialize[Task[I]]](tx) )
			else
				taskMacroImpl[I](c)( c.Expr[I](tx) )
		def wrapTag[I: WeakTypeTag]: WeakTypeTag[Initialize[Task[I]]] = weakTypeTag

		def sub(name: String, tpe: Type, qual: Tree): Converted[c.type] =
		{
			val tag = c.WeakTypeTag[T](tpe)
			InitParserConvert(c)(name, qual)(tag) transform { tree =>
				subWrapper(tpe, tree)
			}
		}

		val tx = util.transformWrappers(ttree, (n,tpe,tree) => sub(n,tpe,tree))
		result match {
			case Some((p, tpe, param)) =>
				val fCore = Function(param :: Nil, tx)
				val bodyTpe = wrapTag(tag).tpe
				val fTpe = util.functionType(tpe :: Nil, bodyTpe)
				val fTag = c.WeakTypeTag[Any](fTpe) // don't know the actual type yet, so use Any
				val fInit = c.resetLocalAttrs( expandTask(false, fCore)(fTag).tree )
				inputTaskCreate(InputTaskCreateDynName, tpe, tag.tpe, p, fInit)
			case None =>
				val init = c.resetLocalAttrs( expandTask[T](true, tx).tree )
				inputTaskCreateFree(tag.tpe, init)
		}
	}
}

object PlainTaskMacro
{
	def task[T](t: T): Task[T] = macro taskImpl[T]
	def taskImpl[T: c.WeakTypeTag](c: Context)(t: c.Expr[T]): c.Expr[Task[T]] = 
		Instance.contImpl[T,Id](c, TaskInstance, TaskConvert, MixedBuilder)(Left(t), Instance.idTransform[c.type])

	def taskDyn[T](t: Task[T]): Task[T] = macro taskDynImpl[T]
	def taskDynImpl[T: c.WeakTypeTag](c: Context)(t: c.Expr[Task[T]]): c.Expr[Task[T]] = 
		Instance.contImpl[T,Id](c, TaskInstance, TaskConvert, MixedBuilder)(Right(t), Instance.idTransform[c.type])
}
