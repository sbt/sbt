package sbt
package std

	import language.experimental.macros
	import scala.reflect._
	import reflect.macros._

private[sbt] object KeyMacro
{
	def settingKeyImpl[T: c.WeakTypeTag](c: Context)(description: c.Expr[String]): c.Expr[SettingKey[T]] =
		keyImpl[T, SettingKey[T]](c) { (name, mf) =>
			c.universe.reify { SettingKey[T](name.splice, description.splice)(mf.splice) }
		}
	def taskKeyImpl[T: c.WeakTypeTag](c: Context)(description: c.Expr[String]): c.Expr[TaskKey[T]] =
		keyImpl[T, TaskKey[T]](c) { (name, mf) =>
			c.universe.reify { TaskKey[T](name.splice, description.splice)(mf.splice) }
		}
	def inputKeyImpl[T: c.WeakTypeTag](c: Context)(description: c.Expr[String]): c.Expr[InputKey[T]] =
		keyImpl[T, InputKey[T]](c) { (name, mf) =>
			c.universe.reify { InputKey[T](name.splice, description.splice)(mf.splice) }
		}

	def keyImpl[T: c.WeakTypeTag, S: c.WeakTypeTag](c: Context)(f: (c.Expr[String], c.Expr[Manifest[T]]) => c.Expr[S]): c.Expr[S] =
	{
		import c.universe.{Apply=>ApplyTree,_}
		val enclosingValName = definingValName(c, methodName => s"""$methodName must be directly assigned to a val, such as `val x = $methodName[Int]("description")`.""")
		val name = c.Expr[String]( Literal(Constant(enclosingValName)) )
		val mf = c.Expr[Manifest[T]](c.inferImplicitValue( weakTypeOf[Manifest[T]] ) )
		f(name, mf)
	}
	def definingValName(c: Context, invalidEnclosingTree: String => String): String =
	{
		import c.universe.{Apply=>ApplyTree,_}
		val methodName = c.macroApplication.symbol.name
		def processName(n: Name): String = n.decoded.trim // trim is not strictly correct, but macros don't expose the API necessary
		def enclosingVal(trees: List[c.Tree]): String =
		{
			trees match {
				case vd @ ValDef(_, name, _, _) :: ts => processName(name)
				case (_: ApplyTree | _: Select | _: TypeApply) :: xs => enclosingVal(xs)
					// lazy val x: X = <methodName> has this form for some reason (only when the explicit type is present, though)
				case Block(_, _) :: DefDef(mods, name, _, _, _, _) :: xs if mods.hasFlag(Flag.LAZY) => processName(name)
				case _ =>
					c.error(c.enclosingPosition, invalidEnclosingTree(methodName.decoded))
					"<error>"
			}
		}
		enclosingVal(enclosingTrees(c).toList)
	}
	def enclosingTrees(c: Context): Seq[c.Tree] =
		c.asInstanceOf[reflect.macros.runtime.Context].callsiteTyper.context.enclosingContextChain.map(_.tree.asInstanceOf[c.Tree])
}