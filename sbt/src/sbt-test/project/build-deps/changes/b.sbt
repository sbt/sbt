buildDependencies in Global := ((buildDependencies in Global, thisProjectRef, thisProjectRef in LocalProject("a")) { (deps, refB, refA) =>
	deps.addClasspath(refA, ResolvedClasspathDependency(refB, None))
}).value
