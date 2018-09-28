package coursier

object Helper {

  def checkEmpty(): Boolean = {
    Tasks.resolutionsCache.isEmpty && Tasks.reportsCache.isEmpty
  }

}