// TODO: join, reduce
package sbt
package std

	import Types._
	import Task._

object Cross
{
	type AttributeSet = Set[AttributeKey[_]]

	def hasCross(s: Seq[Task[_]]): Boolean = s.exists { _.work.isInstanceOf[CrossAction[_]] }

	def extract[T](in: AttributeMap = AttributeMap.empty): Task[T] => Task[T] = (result: Task[T]) =>
		result.work match
		{
			case CrossAction(subs) =>
				subs.filter { x => compatible(in)(x._1) } match {
					case Seq( (_,matchingTask) ) => matchingTask
					case Seq() => if(subs.isEmpty) error("Cannot introduce cross configurations with flatMap") else error("No compatible cross configurations returned by " + result)
					case _ => error("Multiple compatible cross configurations returned by " + result)
				}
			case _ => result
		}

	def compatible(base: AttributeMap)(nested: AttributeMap): Boolean =
		nested.entries forall { case AttributeEntry(key, v) => base get(key) filter (_ == v) isDefined }

	def expandUniform[S](uniform: Seq[Task[S]]): Cross[ Seq[Task[S]] ] =
		// the cast is necessary to avoid duplicating the contents of expandExist
		// and expandCrossed (expandExist cannot be defined in terms of expandUniform
		//  because there is no valid instatiation of S)
		expandExist(uniform).asInstanceOf[ Cross[ Seq[Task[S]] ] ]

	def expandExist(dependsOn: Seq[Task[_]]): Cross[ Seq[Task[_]] ] =
	{
		val klist = KList.fromList(dependsOn)
		map( expandCrossed( klist ) ) { _.toList }
	}

	def keys(c: Cross[_]): AttributeSet =
		(Set.empty[AttributeKey[_]] /: c ){ _ ++ _._1.keys }

	def uniform[S,T](in: Seq[Task[S]])(f: (AttributeMap, Seq[Task[S]]) => Task[T]): Task[T] =
		crossTask( expandUniform(in), f)

	def exist[T](in: Seq[Task[_]])(f: (AttributeMap, Seq[Task[_]]) => Task[T]): Task[T] =
		crossTask( expandExist(in), f)
		
	def apply[T, HL <: HList](in: Tasks[HL])(f: (AttributeMap, Tasks[HL]) => Task[T]): Task[T] =
		crossTask( expandCrossed(in), f)
		
	def crossTask[S,T](subs: Cross[S], f: (AttributeMap, S) => Task[T]): Task[T] =
		crossTask( subs map { case (m, t) => (m, f(m, t)) } )
		
	def crossTask[T](subs: Cross[Task[T]]): Task[T] =
		TaskExtra.actionToTask( CrossAction(subs) )

	def expandCrossed[HL <: HList](in: Tasks[HL]): Cross[Tasks[HL]] =
		in match {
			case KCons(head, tail) =>
				val crossTail = expandCrossed(tail)
				head.work match {
					case CrossAction(subs) => combine(subs, crossTail)(KCons.apply)
					case _ => crossTail map { case (m, k) => (m, KCons(head, k) ) }
				}
			case x => (AttributeMap.empty, x) :: Nil
		}
	
	
	def combine[A,B,C](a: Cross[A], b: Cross[B])(f: (A,B) => C): Cross[C] =
	{
		val keysA = keys(a)
		val keysB = keys(b)
		val common = keysA & keysB
		if( keysA.size > keysB.size )
			merge(b,a, common)( (x,y) => f(y,x) )
		else
			merge(a,b, common)(f)
	}
	private[this] def merge[A,B,C](a: Cross[A], b: Cross[B], common: AttributeSet)(f: (A,B) => C): Seq[(AttributeMap, C)] =
	{
		def related(mapA: AttributeMap): Cross[B] =
			b filter { case (mapB, _) => 
				common forall ( c => mapA(c) == mapB(c) )
			}
		def check(aRb: Cross[B]) = if(aRb.isEmpty) error("Cross mismatch") else aRb
			
		for {
			(mapA, taskA) <- a
			(mapB, taskB) <- check(related(mapA)) 
		} yield
			( mapA ++ mapB, f(taskA, taskB) )
	}

	def map[A,B](c: Cross[A])(f: A => B):  Cross[B] =
		for( (m, a) <- c) yield (m, f(a) )
}