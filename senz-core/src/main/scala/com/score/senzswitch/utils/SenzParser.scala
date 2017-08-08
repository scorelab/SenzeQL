package com.score.senzswitch.utils

import com.score.senzswitch.protocols.{Senz, SenzType}

object SenzParser {

  def parseSenz(senzMsg: String) = {
    val tokens = senzMsg.trim.split(" ")

    val senzType = getSenzType(tokens)
    val signature = getSignature(tokens.drop(1))
    val sender = getSender(tokens.drop(1).dropRight(1)).toLowerCase
    val receiver = getReceiver(tokens.drop(1).dropRight(2)).toLowerCase
    val attr = getAttributes(tokens.drop(1).dropRight(3))

    Senz(senzType, sender, receiver, attr, signature)
  }

  private def getSenzType(tokes: Array[String]) = {
    SenzType.withName(tokes.head.trim)
  }

  private def getSignature(tokens: Array[String]) = {
    Some(tokens.last.trim)
  }

  private def getSender(tokens: Array[String]) = {
    tokens.find(_.startsWith("^")).get.trim.substring(1)
  }

  private def getReceiver(tokens: Array[String]) = {
    tokens.find(_.startsWith("@")).get.trim.substring(1)
  }

  private def getAttributes(tokens: Array[String], attr: Map[String, String] = Map[String, String]()): Map[String, String] = {
    tokens match {
      case Array() =>
        // empty array
        attr
      case Array(_) =>
        // last index
        if (tokens(0).startsWith("#")) attr + (tokens(0) -> "") else attr
      case Array(_, _*) =>
        // have at least two elements
        if (tokens(0).startsWith("$")) {
          // $key 5.23
          getAttributes(tokens.drop(2), attr + (tokens(0) -> tokens(1)))
        } else if (tokens(0).startsWith("#")) {
          if (tokens(1).startsWith("#") || tokens(1).startsWith("$")) {
            // #lat $key 23.23
            // #lat #lon
            getAttributes(tokens.drop(1), attr + (tokens(0) -> ""))
          } else {
            // #lat 3.342
            getAttributes(tokens.drop(2), attr + (tokens(0) -> tokens(1)))
          }
        } else {
          attr
        }
    }
  }

  def composeSenz(senz: Senz): String = {
    // attributes comes as
    //    1. #lat 3.432 #lon 23.343
    //    2. #lat #lon
    var attr = ""
    for ((k, v) <- senz.attributes) {
      attr += s"$k $v".trim + " "
    }

    s"${senz.senzType} ${attr.trim} @${senz.receiver} ^${senz.sender} ${senz.signature}"
  }

}

//object Main extends App {
//  val s = SenzParser.parseSenz("DATA #uid 701199546 #msg dhhd #time 1475851199 @lala ^hell MULQZ1jk9LUQ6/t72xeMeH40MLrE5TR9vAiBPnCa2tJMPRKbcT5AXfly9eR4Kaj1V93JdFLDz8be4rx0V91wUJxvSRT6FHWV6kk2tVlR90YYfWjwOeVr8aqDsLpRoHvwNJPIp+HU3/08tXXhghXFY0IvXrvV4viq+9mxkm6/IL0=")
//  println(s.attributes)
//  //println(SenzParser.compose(s))
//  //shareStore.share(Array("eranga", "her"), "sdfs", "wer")
//  //shareStore.isShared("at", "sdf", "sdf")
//}
