package com.score.senzswitch.components

import akka.actor.ActorRef

/**
 * Created by eranga on 5/20/16.
 */
trait ActorStoreComp {

  val actorStore: ActorStore

  trait ActorStore {
    def getActor(name: String): Option[ActorRef]

    def addActor(name: String, actor: ActorRef)

    def removeActor(name: String)
  }

}

