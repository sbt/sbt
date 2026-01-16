package sbt.internal.librarymanagement
package ivyint

import org.apache.ivy.util.url.CredentialsStore
import scala.jdk.CollectionConverters.*

/** A key used to store credentials in the ivy credentials store. */
private[sbt] sealed trait CredentialKey

/** Represents a key in the ivy credentials store that is only specific to a host. */
private[sbt] case class Host(name: String) extends CredentialKey

/** Represents a key in the ivy credentials store that is keyed to both a host and a "realm". */
private[sbt] case class Realm(host: String, realm: String) extends CredentialKey

/**
 * Helper mechanism to improve credential related error messages.
 *
 * This evil class exposes to us the necessary information to warn on credential failure and offer
 * spelling/typo suggestions.
 */
private[sbt] object IvyCredentialsLookup {

  /** Helper extractor for Ivy's key-value store of credentials. */
  private object KeySplit {
    def unapply(key: String): Option[(String, String)] = {
      key.indexOf('@') match {
        case -1 => None
        case n  => Some(key.take(n) -> key.drop(n + 1))
      }
    }
  }

  /**
   * Here we cheat runtime private so we can look in the credentials store.
   *
   *  TODO - Don't bomb at class load time...
   */
  private val credKeyringField = {
    val tmp = classOf[CredentialsStore].getDeclaredField("KEYRING")
    tmp.setAccessible(true)
    tmp
  }

  /** All the keys for credentials in the ivy configuration store. */
  def keyringKeys: Set[CredentialKey] = {
    val map = credKeyringField.get(null).asInstanceOf[java.util.HashMap[String, Any]]
    // make a clone of the set...
    (map.keySet.asScala.map {
      case KeySplit(realm, host) => (Realm(host, realm): CredentialKey)
      case host                  => (Host(host): CredentialKey)
    }).toSet
  }

  /**
   * A mapping of host -> realms in the ivy credentials store.
   */
  def realmsForHost: Map[String, Set[String]] =
    keyringKeys
      .collect { case x: Realm =>
        x
      }
      .groupBy { realm =>
        realm.host
      }
      .view
      .mapValues { realms =>
        realms map (_.realm)
      }
      .toMap
}
