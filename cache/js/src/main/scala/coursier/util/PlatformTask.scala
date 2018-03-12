package coursier.util

abstract class PlatformTask {

  implicit val gather: Gather[Task] =
    new TaskGather {}

}
