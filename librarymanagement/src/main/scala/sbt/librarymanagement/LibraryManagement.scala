package sbt.librarymanagement

// Interface for library management

trait LibraryManagement {
  type Module
  def getModule(moduleId: ModuleID): Module
}
