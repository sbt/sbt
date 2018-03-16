package coursier.test

import java.io.InputStream

import com.spotify.docker.client.DefaultDockerClient
import com.spotify.docker.client.messages.{ContainerConfig, HostConfig, PortBinding}
import coursier.internal.FileUtil

import scala.annotation.tailrec
import scala.collection.JavaConverters._
import scala.concurrent.duration.DurationInt
import scala.util.Try

final case class NexusDocker(base: String, shutdown: () => Unit)

object NexusDocker {
  def apply(
    image: String,
    basePath: String,
    // can't find a way to get back a randomly assigned port (even following https://github.com/spotify/docker-client/issues/625)
    // so that one has to be specified
    hostPort: Int
  ): NexusDocker = {

    val addr = s"localhost:$hostPort"

    def log(s: String): Unit =
      Console.err.println(s"[$image @ $addr] $s")

    val docker = DefaultDockerClient.fromEnv().build()
    docker.pull(image)

    val portBindings = Map("8081" -> Seq(PortBinding.of("0.0.0.0", hostPort)).asJava)

    val hostConfig = HostConfig.builder().portBindings(portBindings.asJava).build()

    val containerConfig = ContainerConfig.builder()
      .hostConfig(hostConfig)
      .image(image)
      .exposedPorts(portBindings.keys.toSeq: _*)
      .build()

    var idOpt = Option.empty[String]

    def shutdown(): Unit =
      for (id <- idOpt) {
        Try(docker.killContainer(id))
        docker.removeContainer(id)
        docker.close()
      }

    try {
      val creation = docker.createContainer(containerConfig)

      val id = creation.id()
      idOpt = Some(id)

      log(s"starting container $id")
      docker.startContainer(id)

      val base: String =
        s"http://localhost:$hostPort/$basePath"

      log(s"waiting for nexus server to be up-and-running")

      val retryDuration = 2.seconds

      @tailrec
      def loop(retry: Int): Unit =
        if (retry > 0) {
          val url = new java.net.URL(base)
          var is: InputStream = null
          try {
            is = url.openStream()
            FileUtil.readFully(is)
            log("nexus up")
          } catch {
            case e: java.io.IOException =>
              if (is != null) is.close()
              log(s"Caught $e, retrying in $retryDuration")
              Thread.sleep(retryDuration.toMillis)
              loop(retry - 1)
          }
        } else
          throw new Exception(s"Timeout when waiting for container for $image to be up-and-running")

      loop(60)

      NexusDocker(base, () => shutdown())
    } catch {
      case t: Throwable =>
        shutdown()
        throw t
    }
  }
}
