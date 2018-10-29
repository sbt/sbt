
object Main {

  import akka.actor._

  val system = ActorSystem()

  system.terminate()

  import com.typesafe.config.ConfigFactory

  val x = ConfigFactory.load()

}
