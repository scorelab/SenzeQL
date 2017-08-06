package com.score.senzswitch.components

import com.mongodb.casbah.Imports._
import com.mongodb.casbah.commons.{MongoDBList, MongoDBObject}
import com.score.senzswitch.config.{AppConfig, DbConfig}

import scala.annotation.tailrec

/**
  * Created by eranga on 8/13/16.
  */
trait ShareStoreCompImpl extends ShareStoreComp {

  this: DbConfig with AppConfig =>

  val shareStore = new ShareStoreImpl

  class ShareStoreImpl extends ShareStore {

    @tailrec
    final def share(from: String, to: String, attr: List[String]): Boolean = {
      attr match {
        case x :: tail =>
          // update/push
          coll.update(MongoDBObject("name" -> to), $addToSet(x -> from))
          coll.update(MongoDBObject("name" -> from), $addToSet(to -> x))

          // update connected users(cons)
          coll.update(MongoDBObject("name" -> to), $addToSet("cons" -> from))
          coll.update(MongoDBObject("name" -> from), $addToSet("cons" -> to))

          share(from, to, tail)
        case Nil =>
          true
      }
    }

    @tailrec
    final def unshare(from: String, to: String, attr: List[String]): Boolean = {
      attr match {
        case x :: tail =>
          // update/pull
          coll.update(MongoDBObject("name" -> to), $pull(x -> from))
          coll.update(MongoDBObject("name" -> from), $pull(to -> x))

          // TODO update/remove connected users(cons)
          // coll.update(MongoDBObject("name" -> to), $pull("cons" -> from))
          // coll.update(MongoDBObject("name" -> from), $pull("cons" -> to))

          unshare(from, to, tail)
        case Nil =>
          true
      }
    }

    @tailrec
    final def isShared(from: String, to: String, attr: List[String]): Boolean = {
      attr match {
        case x :: tail =>
          val matcher = MongoDBObject("$in" -> MongoDBList(attr))
          val query = MongoDBObject("name" -> from, to -> matcher)
          coll.findOne(query) match {
            case Some(obj) =>
              isShared(from, to, tail)
            case _ =>
              false
          }
        case Nil =>
          true
      }
    }

    def getCons(user: String): List[String] = {
      // read connections of user
      val fields = MongoDBObject("cons" -> 1)
      val query = MongoDBObject("name" -> user)
      coll.findOne(query, fields) match {
        case Some(obj) =>
          obj.getAs[List[String]]("cons").getOrElse(List.empty[String])
        case _ =>
          List.empty[String]
      }
    }

    def insert(name: String, key: String) = {
      val zBuilder = MongoDBObject.newBuilder
      zBuilder += "name" -> name
      zBuilder += "key" -> key

      coll.insert(zBuilder.result())
    }
  }

}

//object Main extends App with ShareStoreCompImpl with DbConfig with AppConfig {
//  shareStore.insert("scala", "skey")
//  shareStore.insert("haskell", "hkey")
//  shareStore.insert("erlang", "hkey")
//
//  shareStore.share("saka", "kaka", List("lat", "lon", "msg", "tell"))
//
//  shareStore.share("haskell", "scala", List("lat", "lon", "msg", "tell"))
//  shareStore.share("erlang", "scala", List("lat", "lon", "msg", "tell"))
//  println(shareStore.getCons("scala"))
//  shareStore.unshare("scala", "haskell", List("lat", "lon"))

//  shareStore.share("scala", "haskell", "lat")
//  shareStore.unsha("scala", "haskell", "lat")
//  shareStore.isShared("scala", "haskell", "lat")

//  shareStore.share("eranga", "lambda", "lat")
//  shareStore.sha("herath", "lambda", "lot")
//  shareStore.isSha("herath", "lambda", "lot")

//  shareStore.share1("lakmal", "eranga", "lon")
//  shareStore.unshare1("lakmal", "eranga", "lon")
//  shareStore.isShared("lakmal", "eranga", "lon")

//  shareStore.share("lakmal", "lambda", List("msg"))
//  shareStore.unshare("lakmal", "lambda", List("lat", "msg"))
//}
