package sbt

import sbt.internal.nio.FileTreeRepository
import sbt.nio.file.{ FileAttributes, Glob }

object OnLoadRepository:
  @volatile private var seen: Option[FileTreeRepository[FileAttributes]] = None

  val record: State => State = state =>
    seen = state.get(nio.Keys.globalFileTreeRepository)
    state

  val check: Command = Command.command("checkOnLoadRepository"): state =>
    val repository = seen.getOrElse(sys.error("onLoad did not see a file tree repository"))
    repository.register(Glob(state.baseDir.toPath)).fold(e => throw e, _.close())
    assert(
      state.get(nio.Keys.globalFileTreeRepository).exists(_ eq repository),
      "onLoad did not see the file tree repository that the loaded build keeps"
    )
    state
end OnLoadRepository
