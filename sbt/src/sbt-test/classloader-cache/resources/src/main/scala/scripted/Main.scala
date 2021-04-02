package scripted

import resource.Resource

object Main {
  def main(args: Array[String]): Unit = {
    val Array(resource, expected) = args
    assert(Resource.getStringResource(resource) == expected)
  }
}
