package sbt.compat

object SbtCompat {
  object librarymanagement
  object internal {
      object librarymanagement
      object util {
         val JLine: { def usingTerminal[T](f: jline.Terminal => T): T } = sbt.JLine
       }
    }
}