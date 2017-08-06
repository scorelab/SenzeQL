package com.score.senzswitch.boot

import akka.actor.ActorSystem
import com.score.senzswitch.actors.{SenzListenerActor, SenzQueueActor}
import com.score.senzswitch.utils.SenzFactory

/**
  * Created by eranga on 1/9/16.
  */
object Main extends App {

  // setup logging
  SenzFactory.setupLogging()

  // setup keys
  SenzFactory.setupKeys()

  // start actor
  implicit val system = ActorSystem("senz")
  val queueRef = system.actorOf(SenzQueueActor.props, name = "SenzQueue")

  system.actorOf(SenzListenerActor.props(queueRef), name = "SenzListener")
}
