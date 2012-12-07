package sbt.inc

// stolen from Josh
object StronglyConnected
{
	def apply[N](nodes: Iterable[N])(dependencies: N => Iterable[N]): Set[Set[N]] =
	{
		val stack = new collection.mutable.Stack[N]
		val onStack = new collection.mutable.HashSet[N]
		val scc = new collection.mutable.ArrayBuffer[Set[N]]
		val index = new collection.mutable.ArrayBuffer[N]
		val lowLink = new collection.mutable.HashMap[N, Int]
	  
		def tarjanImpl(v: N)
		{
			index += v
			lowLink(v) = index.size-1
			stack.push(v)
			onStack += v
			for(n <- dependencies(v))
			{
				if( !index.contains(n) )
				{
					tarjanImpl(n)
					lowLink(v) = math.min(lowLink(v), lowLink(n))
				}
				else if(onStack(n))
					lowLink(v) = math.min(lowLink(v), index.indexOf(n))
			}

			if(lowLink(v) == index.indexOf(v))
			{
				val components = new collection.mutable.ArrayBuffer[N]
				def popLoop()
				{
					val popped = stack.pop()
					onStack -= popped
					components.append(popped)
					if(popped != v) popLoop()
				}
				popLoop()
				scc.append(components.toSet)
			}
		}

		for(node <- nodes)
			if( !index.contains(node) )
				tarjanImpl(node)
		scc.toSet
	}
}
