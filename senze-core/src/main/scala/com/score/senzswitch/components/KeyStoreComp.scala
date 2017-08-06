package com.score.senzswitch.components

import com.score.senzswitch.protocols.{SenzKey, SwitchKey}

/**
 * Created by eranga on 7/15/16.
 */
trait KeyStoreComp {

  val keyStore: KeyStore

  trait KeyStore {

    def putSwitchKey(switchKey: SwitchKey)

    def getSwitchKey: Option[SwitchKey]

    def saveSenzieKey(senzKey: SenzKey)

    def findSenzieKey(name: String): Option[SenzKey]
  }

}
