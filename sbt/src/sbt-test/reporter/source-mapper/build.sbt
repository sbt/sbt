import java.util.Optional
import xsbti.Position

val assertAbsolutePathConversion = taskKey[Unit]("checks source mappers convert to absolute path")

val assertHandleFakePos = taskKey[Unit]("checks source mappers handle fake position")

assertAbsolutePathConversion := {
  val converter = fileConverter.value
  val source = (Compile/sources).value.head
  val position = newPosition(converter.toVirtualFile(source.toPath).id, source)
  val mappedPos = sourcePositionMappers.value
    .foldLeft(Option(position)) {
      case (pos, mapper) => pos.flatMap(mapper)
    }
  assert {
    mappedPos.get.sourcePath.asScala.contains(source.getAbsolutePath)
  }
}

assertHandleFakePos := {
  val position = newPosition("<macro>", new File("<macro>"))
  val mappedPos = sourcePositionMappers.value
    .foldLeft(Option(position)) {
      case (pos, mapper) => pos.flatMap(mapper)
    }
  assert {
    mappedPos.get.sourcePath.asScala.get.contains("<macro>")
  }
}

def newPosition(path: String, file: File): Position = new Position {
  override def line(): Optional[Integer] = Optional.empty()

  override def lineContent() = ""

  override def offset(): Optional[Integer] = Optional.empty()

  override def pointer(): Optional[Integer] = Optional.empty()

  override def pointerSpace(): Optional[String] = Optional.empty()

  override def sourcePath(): Optional[String] = Optional.of(path)

  override def sourceFile(): Optional[File] = Optional.of(file)
}