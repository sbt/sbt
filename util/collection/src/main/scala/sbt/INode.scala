package sbt

	import java.lang.Runnable
	import java.util.concurrent.{atomic, Executor, LinkedBlockingQueue}
	import atomic.{AtomicBoolean, AtomicInteger}
	import Types.{:+:, ConstK, Id}

abstract class EvaluateSettings[Scope]
{
	protected val init: Init[Scope]
	import init._
	protected def compiledSettings: Seq[Compiled[_]]

	private[this] val static = PMap.empty[ScopedKey, INode]
	private[this] val allScopes: Set[Scope] = compiledSettings.map(_.key.scope).toSet
	private[this] def getStatic[T](key: ScopedKey[T]): INode[T] = static get key getOrElse sys.error("Illegal reference to key " + key)

	private[this] val transform: Initialize ~> INode = new (Initialize ~> INode) { def apply[T](i: Initialize[T]): INode[T] = i match {
		case k: Keyed[s, T] => single(getStatic(k.scopedKey), k.transform)
		case a: Apply[k,T] => new MixedNode[k,T]( a.alist.transform[Initialize, INode](a.inputs, transform), a.f, a.alist)
		case b: Bind[s,T] => new BindNode[s,T]( transform(b.in), x => transform(b.f(x)))
		case init.StaticScopes => constant(() => allScopes.asInstanceOf[T]) // can't convince scalac that StaticScopes => T == Set[Scope]
		case v: Value[T] => constant(v.value)
		case t: TransformCapture => constant(() => t.f)
		case o: Optional[s,T] => o.a match {
			case None => constant( () => o.f(None) )
			case Some(i) => single[s,T](transform(i), x => o.f(Some(x)))
		}
	}}
	private[this] lazy val roots: Seq[INode[_]] = compiledSettings flatMap { cs =>
		(cs.settings map { s =>
			val t = transform(s.init)
			static(s.key) = t
			t
		}): Seq[INode[_]]
	}

	def run(implicit delegates: Scope => Seq[Scope]): Settings[Scope] =
	{
		roots.foreach( _.value )
		getResults(delegates)
	}
	private[this] def getResults(implicit delegates: Scope => Seq[Scope]) =
		(empty /: static.toTypedSeq) { case (ss, static.TPair(key, node)) =>
			if(key.key.isLocal) ss else ss.set(key.scope, key.key, node.value)
		}

	private[this] sealed abstract class INode[T]
	{
		final lazy val value: T = {
			val v = evaluate
			if(v == null) sys.error("Setting value cannot be null: " + keyString)
			v
		}

		override def toString = getClass.getName + ": " + keyString

		private[this] def keyString = 
			(static.toSeq.flatMap { case (key, value) => if(value eq this) init.showFullKey(key) :: Nil else Nil }).headOption getOrElse "non-static"

		protected def evaluate: T
	}
	private[this] val getValue = new (INode ~> Id) { def apply[T](i: INode[T]): T = i.value }
	private[this] def constant[T](f: () => T): INode[T] = new MixedNode[ConstK[Unit]#l, T]((), _ => f(), AList.empty)
	private[this] def single[S,T](in: INode[S], f: S => T): INode[T] = new MixedNode[ ({ type l[L[x]] = L[S] })#l, T](in, f, AList.single[S])
	private[this] final class BindNode[S,T](in: INode[S], f: S => INode[T]) extends INode[T]
	{
		protected def evaluate: T = f(in.value).value
	}
	private[this] final class MixedNode[K[L[x]], T](in: K[INode], f: K[Id] => T, alist: AList[K]) extends INode[T]
	{
		protected def evaluate: T = f( alist.transform(in, getValue) )
	}
}
