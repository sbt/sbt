package sbt

	import java.net.URI
	import Load.LoadedBuildUnit

object Resolve
{
	def apply(index: BuildUtil[_], current: ScopeAxis[Reference], key: AttributeKey[_], mask: ScopeMask): Scope => Scope =
	{
		val rs =
			resolveProject(current, mask) _ ::
			resolveExtra(mask) _ ::
			resolveTask(mask) _ ::
			resolveConfig(index, key, mask) _ ::
			Nil
		scope => (scope /: rs) { (s, f) => f(s) }
	}
	def resolveTask(mask: ScopeMask)(scope: Scope): Scope =
		if(mask.task) scope else scope.copy(task = Global)

	def resolveProject(current: ScopeAxis[Reference], mask: ScopeMask)(scope: Scope): Scope =
		if(mask.project) scope else scope.copy(project = current)
	
	def resolveExtra(mask: ScopeMask)(scope: Scope): Scope =
		if(mask.extra) scope else scope.copy(extra = Global)
	
	def resolveConfig[P](index: BuildUtil[P], key: AttributeKey[_], mask: ScopeMask)(scope: Scope): Scope =
		if(mask.config)
			scope
		else
		{
			val (resolvedRef, proj) = scope.project match {
				case Select(ref) =>
					val r = index resolveRef ref
					(Some(r), index.projectFor(r))
				case Global | This =>
					(None, index.rootProject(index.root))
			}
			val task = scope.task.toOption
			val keyIndex = index.keyIndex
			val definesKey = (c: ScopeAxis[ConfigKey]) => keyIndex.keys(resolvedRef, c.toOption.map(_.name), task) contains key.label
			val projectConfigs = index.configurations(proj).map(ck => Select(ck))
			val config: ScopeAxis[ConfigKey] = (Global +: projectConfigs) find definesKey getOrElse Global
			scope.copy(config = config)
		}

		import Load.BuildStructure
		import Project.ScopedKey

	def projectAggregate[Proj](proj: Option[Reference], extra: BuildUtil[Proj], reverse: Boolean): Seq[ProjectRef] =
	{
		val resRef = proj.map(p => extra.projectRefFor(extra.resolveRef(p)))
		resRef.toList.flatMap(ref =>
			if(reverse) extra.aggregates.reverse(ref) else extra.aggregates.forward(ref)
		)
	}

	def aggregateDeps[T, Proj](key: ScopedKey[T], rawMask: ScopeMask, extra: BuildUtil[Proj], reverse: Boolean = false): Seq[ScopedKey[T]] =
	{
		val mask = rawMask.copy(project = true)
		Dag.topologicalSort(key) { k =>
			val kref = k.scope.project
			for( ref <- projectAggregate(kref.toOption, extra, reverse)) yield
			{
				val toResolve = k.scope.copy(project = Select(ref))
				val resolved = apply(extra, Global, k.key, mask)(toResolve)
				ScopedKey(resolved, k.key)
			}
		}
	}

	def aggregates(units: Map[URI, LoadedBuildUnit]): Relation[ProjectRef, ProjectRef] =
	{
		val depPairs =
			for {
				(uri, unit) <- units.toIterable
				project <- unit.defined.values
				ref = ProjectRef(uri, project.id)
				agg <- project.aggregate
			} yield
				(ref, agg)
		Relation.empty ++ depPairs
	}
}
