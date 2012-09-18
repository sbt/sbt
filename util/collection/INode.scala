package sbt

	import java.lang.Runnable
	import java.util.concurrent.{atomic, Executor, LinkedBlockingQueue}
	import atomic.{AtomicBoolean, AtomicInteger}
	import Types.{:+:, Id}

object EvaluationState extends Enumeration {
	val New, Blocked, Ready, Calling, Evaluated = Value
}

abstract class EvaluateSettings[Scope]
{
	protected val init: Init[Scope]
	import init._
	protected def executor: Executor
	protected def compiledSettings: Seq[Compiled[_]]

		import EvaluationState.{Value => EvaluationState, _}

	private[this] val complete = new LinkedBlockingQueue[Option[Throwable]]
	private[this] val static = PMap.empty[ScopedKey, INode]
	private[this] def getStatic[T](key: ScopedKey[T]): INode[T] = static get key getOrElse error("Illegal reference to key " + key)

	private[this] val transform: Initialize ~> INode = new (Initialize ~> INode) { def apply[T](i: Initialize[T]): INode[T] = i match {
		case k: Keyed[s, T] => single(getStatic(k.scopedKey), k.transform)
		case a: Apply[hl,T] => new MixedNode(a.inputs transform transform, a.f)
		case u: Uniform[s, T] => new UniformNode(u.inputs map transform.fn[s], u.f)
		case b: Bind[s,T] => new BindNode[s,T]( transform(b.in), x => transform(b.f(x)))
		case v: Value[T] => constant(v.value)
		case o: Optional[s,T] => o.a match {
			case None => constant( () => o.f(None) )
			case Some(i) => single[s,T](transform(i), x => o.f(Some(x)))
		}
	}}
	private[this] val roots: Seq[INode[_]] = compiledSettings flatMap { cs =>
		(cs.settings map { s =>
			val t = transform(s.init)
			static(s.key) = t
			t
		}): Seq[INode[_]]
	}
	private[this] var running = new AtomicInteger
	private[this] var cancel = new AtomicBoolean(false)

	def run(implicit delegates: Scope => Seq[Scope]): Settings[Scope] =
	{
		assert(running.get() == 0, "Already running")
		startWork()
		roots.foreach( _.registerIfNew() )
		workComplete()
		complete.take() foreach { ex =>
			cancel.set(true)
			throw ex
		}
		getResults(delegates)
	}
	private[this] def getResults(implicit delegates: Scope => Seq[Scope]) =
		(empty /: static.toTypedSeq) { case (ss, static.TPair(key, node)) =>
			if(key.key.isLocal) ss else ss.set(key.scope, key.key, node.get)
		}
	private[this] val getValue = new (INode ~> Id) { def apply[T](node: INode[T]) = node.get }

	private[this] def submitEvaluate(node: INode[_]) = submit(node.evaluate())
	private[this] def submitCallComplete[T](node: BindNode[_, T], value: T) = submit(node.callComplete(value))
	private[this] def submit(work: => Unit): Unit =
	{
		startWork()
		executor.execute(new Runnable { def run = if(!cancel.get()) run0(work) })
	}
	private[this] def run0(work: => Unit): Unit =
	{
		try { work } catch { case e => complete.put( Some(e) ) }
		workComplete()
	}

	private[this] def startWork(): Unit = running.incrementAndGet()
	private[this] def workComplete(): Unit =
		if(running.decrementAndGet() == 0)
			complete.put( None )
	
	private[this] sealed abstract class INode[T]
	{
		private[this] var state: EvaluationState = New
		private[this] var value: T = _
		private[this] val blocking = new collection.mutable.ListBuffer[INode[_]]
		private[this] var blockedOn: Int = 0
		private[this] val calledBy = new collection.mutable.ListBuffer[BindNode[_, T]]

		override def toString = getClass.getName + " (state=" + state + ",blockedOn=" + blockedOn + ",calledBy=" + calledBy.size + ",blocking=" + blocking.size + "): " +
			keyString

		private[this] def keyString = 
			(static.toSeq.flatMap { case (key, value) => if(value eq this) init.showFullKey(key) :: Nil else Nil }).headOption getOrElse "non-static"
	
		final def get: T = synchronized {
			assert(value != null, toString + " not evaluated")
			value
		}
		final def doneOrBlock(from: INode[_]): Boolean = synchronized {
			val ready = state == Evaluated
			if(!ready) blocking += from
			registerIfNew()
			ready	
		}
		final def isDone: Boolean = synchronized { state == Evaluated }
		final def isNew: Boolean = synchronized { state == New }
		final def isCalling: Boolean = synchronized { state == Calling }
		final def registerIfNew(): Unit = synchronized { if(state == New) register() }
		private[this] def register()
		{
			assert(state == New, "Already registered and: " + toString)
			val deps = dependsOn
			blockedOn = deps.size - deps.count(_.doneOrBlock(this))
			if(blockedOn == 0)
				schedule()
			else
				state = Blocked
		}
	
		final def schedule(): Unit = synchronized {
			assert(state == New || state == Blocked, "Invalid state for schedule() call: " + toString)
			state = Ready
			submitEvaluate(this)
		}
		final def unblocked(): Unit = synchronized {
			assert(state == Blocked, "Invalid state for unblocked() call: " + toString)
			blockedOn -= 1
			assert(blockedOn >= 0, "Negative blockedOn: " + blockedOn + " for " + toString)
			if(blockedOn == 0) schedule()
		}
		final def evaluate(): Unit = synchronized { evaluate0() }
		protected final def makeCall(source: BindNode[_, T], target: INode[T]) {
			assert(state == Ready, "Invalid state for call to makeCall: " + toString)
			state = Calling
			target.call(source)
		}
		protected final def setValue(v: T) {
			assert(state != Evaluated, "Already evaluated (trying to set value to " + v + "): " + toString)
			if(v == null) error("Setting value cannot be null: " + keyString)
			value = v
			state = Evaluated
			blocking foreach { _.unblocked() }
			blocking.clear()
			calledBy foreach { node => submitCallComplete(node, value) }
			calledBy.clear()
		}
		final def call(by: BindNode[_, T]): Unit = synchronized {
			registerIfNew()
			state match {
				case Evaluated => submitCallComplete(by, value)
				case _ => calledBy += by
			}
		}
		protected def dependsOn: Seq[INode[_]]
		protected def evaluate0(): Unit
	}
	private[this] def constant[T](f: () => T): INode[T] = new MixedNode[HNil, T](KNil, _ => f())
	private[this] def single[S,T](in: INode[S], f: S => T): INode[T] = new MixedNode[S :+: HNil, T](in :^: KNil, hl => f(hl.head))
	private[this] final class BindNode[S,T](in: INode[S], f: S => INode[T]) extends INode[T]
	{
		protected def dependsOn = in :: Nil
		protected def evaluate0(): Unit = makeCall(this, f(in.get) )
		def callComplete(value: T): Unit = synchronized {
			assert(isCalling, "Invalid state for callComplete(" + value + "): " + toString)
			setValue(value)
		}
	}
	private[this] final class UniformNode[S,T](in: Seq[INode[S]], f: Seq[S] => T) extends INode[T]
	{
		protected def dependsOn = in
		protected def evaluate0(): Unit = setValue( f(in.map(_.get)) )
	}
	private[this] final class MixedNode[HL <: HList, T](in: KList[INode, HL], f: HL => T) extends INode[T]
	{
		protected def dependsOn = in.toList
		protected def evaluate0(): Unit = setValue( f( in down getValue ) )
	}
}
