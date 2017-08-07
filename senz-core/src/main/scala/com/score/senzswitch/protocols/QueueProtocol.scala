package com.score.senzswitch.protocols

import akka.actor.ActorRef

object QueueType extends Enumeration {
  type QueueType = Value
  val SHARE, GET, DATA, STREAM = Value
}

import com.score.senzswitch.protocols.QueueType._

case class Enqueue(qObj: QueueObj)

case class Dequeue(uid: String)

case class QueueObj(uid: String, queueType: QueueType, senzMsg: SenzMsg)

case class Dispatch(actorRef: ActorRef, user: String)

