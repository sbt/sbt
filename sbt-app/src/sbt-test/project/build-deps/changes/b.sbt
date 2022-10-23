Global / buildDependencies :=
  (Global / buildDependencies).value.addClasspath(
    (LocalProject("a") / thisProjectRef).value,
    ResolvedClasspathDependency(thisProjectRef.value, None)
  )
