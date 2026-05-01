lazy val foo = rootProject
  .settings(
    Compile / scalacOptions += "-Xmacro-settings:a:a"
  )
