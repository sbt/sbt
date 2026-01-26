package sbt
package internal
package util

import scala.collection.concurrent.TrieMap

enum ActionCacheEvent:
  case Found(storeName: String)
  case OnsiteTask
  case Error
end ActionCacheEvent

enum CacheEventSummary:
  case Empty
  case Data(
      hits: Seq[(String, Long)],
      hitCount: Long,
      missCount: Long,
      hitRate: Double,
      onsiteCount: Option[Long],
      errorCount: Option[Long]
  )
  override def toString(): String = this match
    case Empty => ""
    case Data(
          hits,
          hitCount,
          missCount,
          hitRate,
          onsiteCount,
          errorCount
        ) =>
      val hitDescs = hits.map {
        case (id, 1) => s"1 $id cache hit"
        case (id, v) => s"$v $id cache hits"
      }.sorted
      val missDesc = onsiteCount.map:
        case 1 => s"1 onsite task"
        case _ => s"$missCount onsite tasks"
      val errorDesc = errorCount.map:
        case 1      => s"1 error"
        case errors => s"$errors errors"
      val descs = hitDescs ++ missDesc ++ errorDesc
      val descsSummary = descs.mkString(", ")
      val hitRateStr = (hitRate * 100.0).floor.toInt
      s"cache $hitRateStr%, $descsSummary"
end CacheEventSummary

class CacheEventLog:
  private val acEvents = TrieMap.empty[ActionCacheEvent, Long]
  private val previousEvents = TrieMap.empty[ActionCacheEvent, Long]

  def append(event: ActionCacheEvent): Unit =
    acEvents.updateWith(event) {
      case None        => Some(1L)
      case Some(count) => Some(count + 1L)
    }

  def clear(): Unit =
    previousEvents.clear()
    previousEvents ++= acEvents
    acEvents.clear()

  def summary: CacheEventSummary = toSummary(acEvents)
  def previous: CacheEventSummary = toSummary(previousEvents)

  def toSummary(events: TrieMap[ActionCacheEvent, Long]): CacheEventSummary =
    if events.isEmpty then CacheEventSummary.Empty
    else
      val total = events.values.sum
      val hits = events.view.collect { case (ActionCacheEvent.Found(id), v) => (id, v) }.toMap
      val hitCount = hits.values.sum
      val missCount = total - hitCount
      val hitRate = if total > 0 then (hitCount.toDouble / total.toDouble) else 0.0
      val onsiteCount = events.get(ActionCacheEvent.OnsiteTask)
      val errorCount = events.get(ActionCacheEvent.Error)
      CacheEventSummary.Data(
        hits.toSeq,
        hitCount,
        missCount,
        hitRate,
        onsiteCount,
        errorCount
      )
end CacheEventLog
