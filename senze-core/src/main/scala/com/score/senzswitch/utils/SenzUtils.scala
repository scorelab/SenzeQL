package com.score.senzswitch.utils

/**
 * Created by eranga on 8/3/16.
 */
object SenzUtils {
  def getPingSenz(receiver: String, sender: String) = {
    val timestamp = (System.currentTimeMillis / 1000).toString
    s"PING #time $timestamp @$receiver ^$sender"
  }
}
