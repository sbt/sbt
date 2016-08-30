lazy val root = (project in file(".")).
  settings(
    scalacOptions <<= scalacOptions
  )
