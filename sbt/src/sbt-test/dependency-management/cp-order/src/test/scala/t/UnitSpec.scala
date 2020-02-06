package t

import com.typesafe.config.ConfigFactory
import org.scalatest.{ MustMatchers, WordSpec }

class UnitSpec extends WordSpec with MustMatchers {
  def conf = ConfigFactory.defaultReference()

  "Config" should {
    "return Akka HTTP server provider" in {
      val serverProvider = conf.getString("play.server.provider")
      serverProvider mustBe "play.core.server.AkkaHttpServerProvider"
    }

    "be able to load Netty settings" in {
      val nettyTransport = conf.getString("play.server.netty.transport")
      nettyTransport mustBe "jdk"
    }
  }
}
