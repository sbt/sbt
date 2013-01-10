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
		val methodName = c.macroApplication.symbol.name.decoded
		def enclosingVal(trees: List[c.Tree]): String =
			trees match {
				case vd @ ValDef(_, name, _, _) :: ts => name.decoded
				case (_: ApplyTree | _: Select | _: TypeApply) :: xs => enclosingVal(xs)
				case _ =>
					c.error(c.enclosingPosition, invalidEnclosingTree(methodName))
					"<error>"
			}
		enclosingVal(enclosingTrees(c).toList)
	}
	def enclosingTrees(c: Context): Seq[c.Tree] =
		c.asInstanceOf[reflect.macros.runtime.Context].callsiteTyper.context.enclosingContextChain.map(_.tree.asInstanceOf[c.Tree])
}