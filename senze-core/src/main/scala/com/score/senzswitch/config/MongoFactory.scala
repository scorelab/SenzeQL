package com.score.senzswitch.config

import com.mongodb.casbah.MongoClient
import com.typesafe.config.ConfigFactory

import scala.util.Try

object MongoFactory {
  // config object
  val config = ConfigFactory.load()

  // mongo db config
  lazy val mongoHost = Try(config.getString("db.mongo.host")).getOrElse("dev.localhost")
  lazy val mongoPort = Try(config.getInt("db.mongo.port")).getOrElse(27017)
  lazy val dbName = Try(config.getString("db.mongo.db-name")).getOrElse("senz")
  lazy val collName = Try(config.getString("db.mongo.coll-name")).getOrElse("senzies")

  // mongo
  lazy val client = MongoClient(mongoHost, mongoPort)
  lazy val senzDb = client(dbName)
  lazy val coll = senzDb(collName)
}

