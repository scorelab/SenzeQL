package com.score.senzswitch.actors

import akka.actor.{Actor, ActorRef, Props}
import com.score.senzswitch.protocols.{Msg, SenzMsg}
import com.score.senzswitch.utils.SenzParser
import org.slf4j.LoggerFactory
import sun.misc.BASE64Encoder

object SenzQueueActor {

  val senzQueue = scala.collection.mutable.ListBuffer[QueueObj]()

  case class Enqueue(qObj: QueueObj)

  case class Dequeue(uid: String)

  case class QueueObj(uid: String, senzMsg: SenzMsg)

  case class Dispatch(actorRef: ActorRef, user: String)

  def props = Props(classOf[SenzQueueActor])

}

class SenzQueueActor extends Actor {

  import SenzQueueActor._

  def logger = LoggerFactory.getLogger(this.getClass)

  override def preStart() = {
    logger.info(s"[_________START ACTOR__________] ${context.self.path}")
  }

  override def postStop() = {
    logger.info(s"[_________STOP ACTOR__________] ${context.self.path}")
  }

  override def receive = {
    case Enqueue(qObj) =>
      logger.debug(s"Enqueue with uid ${qObj.uid}")

      if (qObj.senzMsg.senz.attributes.contains("#cam")) {
        // only keep one #cam message which correspond for receiver and sender in queue
        senzQueue.find(obj => matchSenderReceiver(obj, qObj) && obj.senzMsg.senz.attributes.contains("#cam")) match {
          case Some(obj) =>
          case _ =>
            val oriMsg = new BASE64Encoder().encode(qObj.senzMsg.data.getBytes).replaceAll("\n", "").replaceAll("\r", "")
            val msg = s"DATA #senz $oriMsg @${qObj.senzMsg.senz.receiver} #uid${qObj.uid} ^senzswitch DIGSIG"
            val senz = SenzParser.parseSenz(msg)
            enqueueObj(QueueObj(qObj.uid, SenzMsg(senz, msg)))(qObj.senzMsg.senz.sender)
        }
      } else if (qObj.senzMsg.senz.attributes.contains("#mic")) {
        // only keep one #mic message which correspond for receiver and sender in queue
        senzQueue.find(obj => matchSenderReceiver(obj, qObj) && obj.senzMsg.senz.attributes.contains("#mic")) match {
          case Some(obj) =>
          case _ =>
            val oriMsg = new BASE64Encoder().encode(qObj.senzMsg.data.getBytes).replaceAll("\n", "").replaceAll("\r", "")
            val msg = s"DATA #senz $oriMsg @${qObj.senzMsg.senz.receiver} #uid${qObj.uid} ^senzswitch DIGSIG"
            val senz = SenzParser.parseSenz(msg)
            enqueueObj(QueueObj(qObj.uid, SenzMsg(senz, msg)))(qObj.senzMsg.senz.sender)
        }
      } else if (qObj.senzMsg.senz.attributes.contains("#lat")) {
        // only keep one #lat message which correspond for receiver and sender in queue
        senzQueue.find(obj => matchSenderReceiver(obj, qObj) && qObj.senzMsg.senz.attributes.contains("#lat")) match {
          case Some(obj) =>
          case _ =>
            enqueueObj(qObj)()
        }
      } else if (qObj.senzMsg.senz.attributes.contains("#msg") || qObj.senzMsg.senz.attributes.contains("$msg")) {
        // keep all #msg messages
        enqueueObj(qObj)()
      }
    case Dequeue(uid) =>
      logger.debug(s"Dequeue with uid $uid")

      senzQueue.find(qObj => qObj.uid.equalsIgnoreCase(uid)) match {
        case Some(qObj) =>
          // remove
          senzQueue -= qObj

          // send DELIVERED status back to sender
          // for calls/selfies we don't send status back
          if (!qObj.senzMsg.senz.attributes.contains("#senz")) {
            val payload = s"DATA #status DELIVERED #uid ${qObj.uid} @${qObj.senzMsg.senz.sender} ^senzswitch SIGNATURE"
            SenzListenerActor.actorRefs(qObj.senzMsg.senz.sender).actorRef ! Msg(payload)
          }
        case None =>
          // no matcher
          logger.debug(s"no matching obj for uid - $uid")
      }
    case Dispatch(actorRef, user) =>
      logger.debug(s"Dispatch queued messages to $user")
      // send buffered msgs again to actor
      // not dispatch stream off messages
      senzQueue.filter(qObj => qObj.senzMsg.senz.receiver.equalsIgnoreCase(user)).foreach(s => actorRef ! Msg(s.senzMsg.data))
  }

  private def enqueueObj(qObj: QueueObj)(sender: String = qObj.senzMsg.senz.sender) = {
    senzQueue += qObj

    // send RECEIVED status back to sender
    val payload = s"DATA #status RECEIVED #uid ${qObj.uid} @${qObj.senzMsg.senz.sender} ^senzswitch SIGNATURE"
    SenzListenerActor.actorRefs(sender).actorRef ! Msg(payload)
  }

  private def matchSenderReceiver(qObj1: QueueObj, qObj2: QueueObj) = {
    qObj1.senzMsg.senz.receiver.equalsIgnoreCase(qObj2.senzMsg.senz.receiver) &&
      qObj1.senzMsg.senz.sender.equalsIgnoreCase(qObj2.senzMsg.senz.sender)
  }

}

