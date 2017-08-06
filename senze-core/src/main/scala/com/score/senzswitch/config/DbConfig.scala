package com.score.senzswitch.config

trait DbConfig {
  val client = MongoFactory.client
  val senzDb = MongoFactory.senzDb
  val coll = MongoFactory.coll
}
