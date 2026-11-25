/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal

import com.sun.net.httpserver.{ HttpExchange, HttpHandler, HttpServer }
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets.UTF_8
import java.util.concurrent.atomic.AtomicInteger
import lmcoursier.internal.shaded.coursier.cache.FileCache
import lmcoursier.internal.shaded.coursier.util.{ Artifact, Task }
import sbt.io.IO
import verify.BasicTestSuite

object CoursierRetryTest extends BasicTestSuite {
  test("retry an artifact download after HTTP 429") {
    IO.withTemporaryDirectory { directory =>
      val requests = new AtomicInteger
      val content = "downloaded after throttling".getBytes(UTF_8)
      val server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)
      server.createContext(
        "/artifact.jar",
        new HttpHandler {
          override def handle(exchange: HttpExchange): Unit =
            try {
              if (requests.incrementAndGet() == 1) {
                exchange.getResponseHeaders.add("Retry-After", "1")
                exchange.sendResponseHeaders(429, -1L)
              } else {
                exchange.sendResponseHeaders(200, content.length.toLong)
                exchange.getResponseBody.write(content)
              }
            } finally exchange.close()
        }
      )
      try {
        server.start()
        val cache = FileCache[Task](directory)
        val artifact = Artifact(s"http://127.0.0.1:${server.getAddress.getPort}/artifact.jar")
        val result = cache.file(artifact).run.unsafeRun()(cache.ec)
        assert(result.isRight, result.toString)
        assert(IO.read(result.toOption.get) == new String(content, UTF_8))
        assert(requests.get() == 2)
      } finally server.stop(0)
    }
  }
}
