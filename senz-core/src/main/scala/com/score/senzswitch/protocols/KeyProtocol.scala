package com.score.senzswitch.protocols


object KeyType extends Enumeration {
  type KeyType = Value
  val PUBLIC_KEY, PRIVATE_KEY = Value
}

case class SwitchKey(pubKey: String, privateKey: String)

case class SenzKey(name: String, key: String)

