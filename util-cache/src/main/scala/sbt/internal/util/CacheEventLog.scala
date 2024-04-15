package sbt
package internal
package util

import scala.collection.concurrent.TrieMap

enum ActionCacheEvent:
  case Found(storeName: String)
  case NotFound
end ActionCacheEvent

class CacheEventLog:
  private val acEvents = TrieMap.empty[ActionCacheEvent, Long]
  def append(event: ActionCacheEvent): Unit =
    acEvents.updateWith(event) {
      case None        => Some(1L)
      case Some(count) => Some(count + 1L)
    }
  def clear(): Unit =
    acEvents.clear()

  def summary: String =
    if acEvents.isEmpty then ""
    else
      val total = acEvents.values.sum
      val hit = acEvents.view.collect { case (k @ ActionCacheEvent.Found(_), v) =>
        (k, v)
      }.toMap
      val hitCount = hit.values.sum
      val missCount = total - hitCount
      val hitRate = (hitCount.toDouble / total.toDouble * 100.0).floor.toInt
      val hitDescs = hit.toSeq.map {
        case (ActionCacheEvent.Found(id), 1) => s"1 $id cache hit"
        case (ActionCacheEvent.Found(id), v) => s"$v $id cache hits"
      }.sorted
      val missDescs = missCount match
        case 0 => Nil
        case 1 => Seq(s"$missCount onsite task")
        case _ => Seq(s"$missCount onsite tasks")
      val descs = hitDescs ++ missDescs
      val descsSummary = descs.mkString(", ", ", ", "")
      s"cache $hitRate%$descsSummary"
end CacheEventLog
