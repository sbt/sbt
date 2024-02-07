(Runtime / externalDependencyClasspath) += {
  val converter = fileConverter.value
  converter.toVirtualFile(file("conf").toPath): HashedVirtualFileRef
}