package com.score.senzswitch.components

trait ShareStoreComp {

  val shareStore: ShareStore

  trait ShareStore {
    def share(from: String, to: String, attr: List[String]): Boolean

    def unshare(from: String, to: String, attr: List[String]): Boolean

    def isShared(from: String, to: String, attr: List[String]): Boolean

    def getCons(name: String): List[String]
  }

}
