package com.score.senzswitch.components

import akka.actor.ActorRef

trait ActorStoreCompImpl extends ActorStoreComp {

  val actorStore = new ActorStoreImpl

  object PayzActorStore {
    val actorRefs = scala.collection.mutable.LinkedHashMap[String, ActorRef]()
  }

  class ActorStoreImpl extends ActorStore {

    import PayzActorStore._

    override def getActor(name: String): Option[ActorRef] = {
      actorRefs.get(name)
    }

    override def addActor(name: String, actorRef: ActorRef) = {
      actorRefs.put(name, actorRef)
    }

    override def removeActor(name: String) {
      actorRefs.remove(name)
    }
  }

}
