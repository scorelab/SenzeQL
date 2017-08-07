package com.score.senzswitch.protocols

import akka.actor.ActorRef

case class ActorId(id: Long)

case class Ref(actorRef: ActorRef, actorId: ActorId = ActorId(System.currentTimeMillis / 1000))

