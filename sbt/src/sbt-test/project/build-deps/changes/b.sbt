buildDependencies in Global :=
  (buildDependencies in Global).value.addClasspath(
    (thisProjectRef in LocalProject("a")).value,
    ResolvedClasspathDependency(thisProjectRef.value, None)
  )
