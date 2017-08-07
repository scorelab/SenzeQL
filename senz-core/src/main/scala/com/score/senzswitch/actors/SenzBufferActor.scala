package com.score.senzswitch.actors

import akka.actor.{Actor, ActorRef, Props}
import com.score.senzswitch.config.AppConfig
import com.score.senzswitch.protocols._
import com.score.senzswitch.utils.SenzParser
import org.slf4j.LoggerFactory

import scala.annotation.tailrec

object SenzBufferActor {

  def props(handlerRef: ActorRef) = Props(classOf[SenzBufferActor], handlerRef)

  case class Buf(date: String)

}

class SenzBufferActor(handlerRef: ActorRef) extends Actor with AppConfig {

  import SenzBufferActor._

  def logger = LoggerFactory.getLogger(this.getClass)

  var buffer = new StringBuffer()
  val bufferListener = new BufferListener()

  override def preStart() = {
    logger.info(s"[_________START ACTOR__________] ${context.self.path}")
    bufferListener.start()
  }

  override def postStop() = {
    logger.info(s"[_________STOP ACTOR__________] ${context.self.path}")
    bufferListener.shutdown()
  }

  override def receive = {
    case Buf(data) =>
      buffer.append(data)
      logger.debug(s"Buf to buffer ${buffer.toString}")
  }

  protected class BufferListener extends Thread {
    var isRunning = true

    def shutdown() = {
      logger.info(s"Shutdown BufferListener")
      isRunning = false
    }

    override def run() = {
      logger.info(s"Start BufferListener")

      if (isRunning) listen()
    }

    @tailrec
    private def listen(): Unit = {
      val index = buffer.indexOf(";")
      if (index != -1) {
        val msg = buffer.substring(0, index)
        buffer.delete(0, index + 1)
        logger.debug(s"Got senz from buffer $msg")

        // send message back to handler
        msg match {
          case "TAK" =>
            logger.debug("TAK received")
          case "TIK" =>
            logger.debug("TIK received")
          case "TUK" =>
            logger.debug("TUK received")
          case _ =>
            val senz = SenzParser.parseSenz(msg)
            handlerRef ! SenzMsg(senz, msg)
        }
      }

      if (isRunning) listen()
    }
  }

}
