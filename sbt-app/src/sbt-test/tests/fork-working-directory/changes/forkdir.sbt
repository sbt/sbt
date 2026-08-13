Test / forkOptions := Def.uncached(
  (Test / forkOptions).value.withWorkingDirectory(Some((ThisBuild / baseDirectory).value))
)
