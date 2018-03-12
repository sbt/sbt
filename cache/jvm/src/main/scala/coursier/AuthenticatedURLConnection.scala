package coursier

import java.net.URLConnection

import coursier.core.Authentication

trait AuthenticatedURLConnection extends URLConnection {
  def authenticate(authentication: Authentication): Unit
}
