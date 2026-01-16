(Runtime / externalDependencyClasspath) += Def.uncached {
  val converter = fileConverter.value
  converter.toVirtualFile(file("conf").toPath): HashedVirtualFileRef
}