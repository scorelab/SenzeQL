package com.score.senzswitch.utils

object SenzUtils {
  def getPingSenz(receiver: String, sender: String) = {
    val timestamp = (System.currentTimeMillis / 1000).toString
    s"PING #time $timestamp @$receiver ^$sender"
  }
}
