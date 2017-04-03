package sbt.librarymanagement

trait LibraryManagementSyntax {
  implicit def richUpdateReport(ur: UpdateReport): RichUpdateReport = new RichUpdateReport(ur)
}

object syntax extends LibraryManagementSyntax
