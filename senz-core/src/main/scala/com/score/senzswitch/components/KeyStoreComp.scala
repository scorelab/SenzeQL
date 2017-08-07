package com.score.senzswitch.components

import com.score.senzswitch.protocols.{SenzKey, SwitchKey}

trait KeyStoreComp {

  val keyStore: KeyStore

  trait KeyStore {

    def putSwitchKey(switchKey: SwitchKey)

    def getSwitchKey: Option[SwitchKey]

    def saveSenzieKey(senzKey: SenzKey)

    def findSenzieKey(name: String): Option[SenzKey]
  }

}
