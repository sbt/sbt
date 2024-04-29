package sbt
package internal
package util

import scala.collection.concurrent.TrieMap
import xsbti.VirtualFileRef

enum ActionCacheEvent:
  case Found(storeName: String)
  case OnsiteTask
  case Error
end ActionCacheEvent

case class ActionCacheError(outputFiles: Seq[VirtualFileRef])

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
      val hits = acEvents.view.collect { case (ActionCacheEvent.Found(id), v) => (id, v) }.toMap
      val hitCount = hits.values.sum
      val missCount = total - hitCount
      val hitRate = (hitCount.toDouble / total.toDouble * 100.0).floor.toInt
      val hitDescs = hits.toSeq.map {
        case (id, 1) => s"1 $id cache hit"
        case (id, v) => s"$v $id cache hits"
      }.sorted
      val missDesc = acEvents
        .get(ActionCacheEvent.OnsiteTask)
        .map:
          case 1 => s"1 onsite task"
          case _ => s"$missCount onsite tasks"
      val errorDesc = acEvents
        .get(ActionCacheEvent.Error)
        .map:
          case 1      => s"1 error"
          case errors => s"$errors errors"
      val descs = hitDescs ++ missDesc ++ errorDesc
      val descsSummary = descs.mkString(", ")
      s"cache $hitRate%, $descsSummary"
end CacheEventLog
