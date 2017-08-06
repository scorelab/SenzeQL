package com.score.senzswitch.actors

import akka.actor._
import akka.io.Tcp
import akka.io.Tcp.Event
import akka.util.ByteString
import com.score.senzswitch.actors.SenzBufferActor.Buf
import com.score.senzswitch.actors.SenzQueueActor.{Dequeue, Dispatch, Enqueue, QueueObj}
import com.score.senzswitch.components.{ActorStoreCompImpl, CryptoCompImpl, KeyStoreCompImpl, ShareStoreCompImpl}
import com.score.senzswitch.config.{AppConfig, DbConfig}
import com.score.senzswitch.protocols._
import org.slf4j.LoggerFactory

import scala.collection.mutable.ListBuffer
import scala.concurrent.duration._

object SenzHandlerActor {

  case object SenzAck extends Event

  case object Tak

  case object Tik

  case object Tuk

  def props(connection: ActorRef, queueRef: ActorRef) = Props(classOf[SenzHandlerActor], connection, queueRef)
}

class SenzHandlerActor(connection: ActorRef, queueRef: ActorRef) extends Actor with KeyStoreCompImpl with CryptoCompImpl with ActorStoreCompImpl with ShareStoreCompImpl with DbConfig with AppConfig {

  import SenzHandlerActor._
  import context._

  def logger = LoggerFactory.getLogger(this.getClass)

  var actorName: String = _
  var actorRef: Ref = _

  var buffRef: ActorRef = _

  // keep msgs when waiting for an ack
  var waitingMsgBuffer = new ListBuffer[Msg]

  context watch connection

  val tikCancel = system.scheduler.schedule(60.seconds, 120.seconds, self, Msg("TIK"))

  override def preStart() = {
    logger.info(s"[_________START ACTOR__________] ${context.self.path}")

    buffRef = context.actorOf(SenzBufferActor.props(self))
  }

  override def postStop() = {
    logger.info(s"[_________STOP ACTOR__________] ${context.self.path} of $actorName")

    // remove ref
    if (actorName != null) {
      SenzListenerActor.actorRefs.get(actorName) match {
        case Some(Ref(_, actorId)) =>
          if (actorRef.actorId.id == actorId.id) {
            // same actor, so remove it
            SenzListenerActor.actorRefs.remove(actorName)

            logger.debug(s"Remove actor with id $actorId")
          } else {
            logger.debug(s"Nothing to remove actor id mismatch $actorId : ${actorRef.actorId.id}")
          }
        case None =>
          logger.debug(s"No actor found with $actorName")
      }
    }
  }

  override def receive: Receive = {
    case Tcp.Received(senzIn) =>
      val senz = senzIn.decodeString("UTF-8")
      logger.debug("Senz received " + senz)

      val buf = Buf(senz)
      buffRef ! buf
    case Tcp.PeerClosed =>
      logger.info("Peer Closed")
      context stop self
    case Msg(data) =>
      if (actorName != null) {
        // send data when only having actor name
        logger.debug(s"Send senz message $data to user $actorName with SenzAck")
        connection ! Tcp.Write(ByteString(s"$data;"), SenzAck)
        context.become({
          case Tcp.Received(senzIn) =>
            val senz = senzIn.decodeString("UTF-8")
            logger.debug(s"Senz received while while waiting for ack: $senz")

            val buf = Buf(senz)
            buffRef ! buf
          case Tcp.PeerClosed =>
            context stop self
          case msg: Msg =>
            logger.debug(s"Msg received while waiting for ack: ${msg.data}")
            waitingMsgBuffer += msg
          case SenzAck =>
            logger.debug("Ack received")
            if (waitingMsgBuffer.isEmpty) {
              logger.debug("Empty buffer")
              context unbecome()
            } else {
              logger.debug("Non empty buffer, write again")
              val w = waitingMsgBuffer.head
              waitingMsgBuffer.remove(0)
              connection ! Tcp.Write(ByteString(s"${w.data};"), SenzAck)
            }
          case SenzMsg(senz: Senz, msg: String) =>
            logger.debug(s"SenzMsg received while waiting for ack: $msg")
            onSenzMsg(senz, msg)
        }, discardOld = false)
      } else {
        // no actor name to send data
        logger.error(s"No actor name to send data: $data")
      }
    case SenzMsg(senz: Senz, msg: String) =>
      logger.debug(s"SenzMsg received $msg")
      onSenzMsg(senz, msg)
  }

  def onSenzMsg(senz: Senz, msg: String) = {
    senz match {
      case Senz(SenzType.SHARE, sender, receiver, attr, signature) =>
        onShare(SenzMsg(senz, msg))
      case Senz(SenzType.GET, sender, receiver, attr, signature) =>
        onGet(SenzMsg(senz, msg))
      case Senz(SenzType.DATA, sender, receiver, attr, signature) =>
        onData(SenzMsg(senz, msg))
      case Senz(SenzType.PUT, sender, receiver, attr, signature) =>
        onPut(SenzMsg(senz, msg))
      case Senz(SenzType.STREAM, sender, receiver, attr, signature) =>
        onStream(SenzMsg(senz, msg))
      case Senz(SenzType.PING, sender, receiver, attr, signature) =>
        onPing(SenzMsg(senz, msg))
    }
  }

  def onShare(senzMsg: SenzMsg) = {
    val senz = senzMsg.senz
    logger.debug(s"SHARE from senzie ${senz.sender} to ${senz.receiver}")

    senz.receiver match {
      case `switchName` =>
        // should be public key sharing
        // store public key, store actor
        val senzSender = senz.sender
        val key = senz.attributes("#pubkey")
        keyStore.findSenzieKey(senzSender) match {
          case Some(SenzKey(`senzSender`, `key`)) =>
            logger.debug(s"Have senzie with name $senzSender and key $key")

            // popup refs
            actorName = senzMsg.senz.sender
            actorRef = Ref(self)
            SenzListenerActor.actorRefs.put(actorName, actorRef)

            logger.debug(s"added ref with ${actorRef.actorId.id}")

            // share from already registered senzie
            val payload = s"DATA #status REG_ALR #pubkey ${keyStore.getSwitchKey.get.pubKey} @${senz.sender} ^${senz.receiver}"
            self ! Msg(crypto.sing(payload))
          case Some(SenzKey(_, _)) =>
            logger.error(s"Have registered senzie with name $senzSender")

            // user already exists
            // send error directly(without ack)
            val payload = s"DATA #status REG_FAIL #pubkey ${keyStore.getSwitchKey.get.pubKey} @${senz.sender} ^${senz.receiver}"
            self ! Msg(crypto.sing(payload))
          case _ =>
            logger.debug("No senzies with name " + senzMsg.senz.sender)

            // popup refs
            actorName = senzMsg.senz.sender
            actorRef = Ref(self)
            SenzListenerActor.actorRefs.put(actorName, actorRef)

            logger.debug(s"added ref with ${actorRef.actorId.id}")

            keyStore.saveSenzieKey(SenzKey(senz.sender, senz.attributes("#pubkey")))

            logger.debug(s"Registration done of senzie $actorName")

            // reply share done msg
            val payload = s"DATA #status REG_DONE #pubkey ${keyStore.getSwitchKey.get.pubKey} @${senz.sender} ^${senz.receiver}"
            self ! Msg(crypto.sing(payload))
        }
      case _ =>
        // share senz for other senzie
        // forward senz to receiver
        logger.debug(s"SHARE from senzie $actorName")
        if (SenzListenerActor.actorRefs.contains(senz.receiver)) {
          // mark as shared attributes
          // shareStore.share(senz.sender, senz.receiver, senz.attributes.keySet.toList)

          SenzListenerActor.actorRefs(senz.receiver).actorRef ! Msg(senzMsg.data)
        } else {
          logger.error(s"Store NOT contains actor with " + senz.receiver)

          // send offline message back
          val payload = s"DATA #status offline #name ${senz.receiver} @${senz.sender} ^senzswitch"
          self ! Msg(crypto.sing(payload))
        }
    }
  }

  def onGet(senzMsg: SenzMsg) = {
    val senz = senzMsg.senz
    logger.debug(s"GET from senzie ${senz.sender} to ${senz.receiver}")

    senz.receiver match {
      case `switchName` =>
        if (senz.attributes.contains("#pubkey")) {
          // public key of user
          // should be request for public key of other senzie
          // find senz key and send it back
          val user = senz.attributes("#name")
          val key = keyStore.findSenzieKey(user).get.key
          val uid = senz.attributes("#uid")
          val payload = s"DATA #pubkey $key #name $user #uid $uid @${senz.sender} ^${senz.receiver}"
          self ! Msg(crypto.sing(payload))
        } else if (senz.attributes.contains("#status")) {
          // user online/offline status
          val user = senz.attributes("#name")
          val status = SenzListenerActor.actorRefs.contains(user)
          val payload = s"DATA #status $status #name $user @${senz.sender} ^${senz.receiver}"
          self ! Msg(crypto.sing(payload))
        }
      case _ =>
        // get senz for other senzie
        // queue it first (only for #cam and #mic)
        if (senz.attributes.contains("#cam") || senz.attributes.contains("#mic") || senz.attributes.contains("lat"))
          queueRef ! Enqueue(QueueObj(senz.attributes("#uid"), senzMsg))

        // forward senz to receiver
        if (SenzListenerActor.actorRefs.contains(senz.receiver)) {
          logger.debug(s"Store contains actor with " + senz.receiver)
          SenzListenerActor.actorRefs(senz.receiver).actorRef ! Msg(senzMsg.data)
        } else {
          logger.error(s"Store NOT contains actor with " + senz.receiver)

          // send offline message back
          val payload = s"DATA #status OFFLINE #name ${senz.receiver} @${senz.sender} ^senzswitch"
          self ! Msg(crypto.sing(payload))
        }
    }
  }

  def onData(senzMsg: SenzMsg) = {
    val senz = senzMsg.senz
    logger.debug(s"DATA from senzie ${senz.sender} to ${senz.receiver}")

    senz.receiver match {
      case `switchName` =>
        // this is status(delivery status most probably)
        // dequeue
        queueRef ! Dequeue(senz.attributes("#uid"))
      case _ =>
        // enqueue only DATA senz with values(not status)
        if (senz.attributes.contains("#msg") || senz.attributes.contains("$msg"))
          queueRef ! Enqueue(QueueObj(senz.attributes("#uid"), senzMsg))

        // forward message to receiver
        // send status back to sender
        if (SenzListenerActor.actorRefs.contains(senz.receiver)) {
          logger.debug(s"Store contains actor with " + senz.receiver)
          SenzListenerActor.actorRefs(senz.receiver).actorRef ! Msg(senzMsg.data)
        } else {
          logger.error(s"Store NOT contains actor with " + senz.receiver)

          // send OFFLINE status back to sender
          val payload = s"DATA #status OFFLINE #name ${senz.receiver} @${senz.sender} ^senzswitch"
          self ! Msg(crypto.sing(payload))
        }
    }
  }

  def onPut(senzMsg: SenzMsg) = {
    val senz = senzMsg.senz
    logger.debug(s"PUT from senzie ${senz.sender} to ${senz.receiver}")

    // forward message to receiver
    // send status back to sender
    if (SenzListenerActor.actorRefs.contains(senz.receiver)) {
      logger.debug(s"Store contains actor with " + senz.receiver)
      SenzListenerActor.actorRefs(senz.receiver).actorRef ! Msg(senzMsg.data)
    } else {
      logger.error(s"Store NOT contains actor with " + senz.receiver)

      // send OFFLINE status back to sender
      val payload = s"DATA #status OFFLINE #name ${senz.receiver} @${senz.sender} ^senzswitch"
      self ! Msg(crypto.sing(payload))
    }
  }

  def onStream(senzMsg: SenzMsg) = {
    val senz = senzMsg.senz
    logger.debug(s"STREAM from senzie ${senz.sender} to ${senz.receiver}")

    if (SenzListenerActor.actorRefs.contains(senz.receiver)) {
      logger.debug(s"Store contains actor with " + senz.receiver)

      // not verify streams, instead directly send them
      SenzListenerActor.actorRefs(senz.receiver).actorRef ! Msg(senzMsg.data)
    } else {
      logger.error(s"Store NOT contains actor with " + senz.receiver)
    }

    // send status back for end stream
    if (senz.attributes.exists(_._2 == "off")) {
      val payload = s"DATA #status RECEIVED #uid ${senz.attributes("#uid")} @${senz.sender} ^senzswitch SIGNATURE"
      self ! Msg(crypto.sing(payload))
    }
  }

  def onPing(senzMsg: SenzMsg) = {
    val senz = senzMsg.senz
    logger.info(s"PING from senzie ${senz.sender}")

    // popup refs
    actorName = senz.sender
    actorRef = Ref(self)
    SenzListenerActor.actorRefs.put(actorName, actorRef)

    logger.debug(s"added ref with ${actorRef.actorId.id}")

    // send TAK on connect
    self ! Msg("TAK")

    // ping means reconnect
    // dispatch queued messages
    queueRef ! Dispatch(self, actorName)
  }

}
