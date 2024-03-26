TaskKey[Unit]("check212") := checkCp(true)
TaskKey[Unit]("check213") := checkCp(false)

def checkCp(auto: Boolean) = Def.task {
  val opts = compilers.value.scalac.classpathOptions
  assert(opts.autoBoot == auto, opts)
  assert(opts.filterLibrary == auto, opts)
  ()
}
