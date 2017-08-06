package com.score.senzswitch.components

import java.io.{File, PrintWriter}

import com.mongodb.casbah.Imports._
import com.score.senzswitch.config.{AppConfig, DbConfig}
import com.score.senzswitch.protocols.{SenzKey, SwitchKey}

/**
  * Created by eranga on 7/15/16.
  */
trait KeyStoreCompImpl extends KeyStoreComp {

  this: DbConfig with AppConfig =>

  val keyStore = new KeyStoreImpl()

  object KeyStoreImpl {
    val BEGIN_PUBLIC_KEY = "-----BEGIN PUBLIC KEY-----"
    val END_PUBLIC_KEY = "-----END PUBLIC KEY-----"
    val BEGIN_PRIVATE_KEY = "-----BEGIN RSA PRIVATE KEY-----"
    val END_PRIVATE_KEY = "-----END RSA PRIVATE KEY-----"
  }

  class KeyStoreImpl extends KeyStore {

    import KeyStoreImpl._

    override def putSwitchKey(switchKey: SwitchKey) = {
      // first create .keys directory
      val dir: File = new File(keysDir)
      if (!dir.exists) {
        dir.mkdir
      }

      // save public key
      val publicKeyStream = new PrintWriter(new File(publicKeyLocation))
      publicKeyStream.write(s"$BEGIN_PUBLIC_KEY\n")
      publicKeyStream.write(s"${switchKey.pubKey}\n")
      publicKeyStream.write(END_PUBLIC_KEY)
      publicKeyStream.flush()
      publicKeyStream.close()

      // save private key
      val privateKeyStream = new PrintWriter(new File(privateKeyLocation))
      privateKeyStream.write(s"$BEGIN_PRIVATE_KEY\n")
      privateKeyStream.write(s"${switchKey.privateKey}\n")
      privateKeyStream.write(BEGIN_PRIVATE_KEY)
      privateKeyStream.flush()
      privateKeyStream.close()
    }

    override def getSwitchKey: Option[SwitchKey] = {
      try {
        // pubkey
        val pubKeySource = scala.io.Source.fromFile(publicKeyLocation)
        val pubKey = pubKeySource.mkString.replaceAll(BEGIN_PUBLIC_KEY, "").replaceAll(END_PUBLIC_KEY, "").replaceAll("\n", "")
        pubKeySource.close()

        // private key
        val privateKeySource = scala.io.Source.fromFile(privateKeyLocation)
        val privateKey = privateKeySource.mkString.replaceAll(BEGIN_PRIVATE_KEY, "").replaceAll(END_PRIVATE_KEY, "").replaceAll("\n", "")
        privateKeySource.close()

        Some(SwitchKey(pubKey, privateKey))
      } catch {
        case e: Throwable =>
          None
      }
    }

    override def saveSenzieKey(senzKey: SenzKey) = {
      // save key in db
      val query = MongoDBObject("name" -> senzKey.name, "key" -> senzKey.key)
      coll.insert(query)
    }

    override def findSenzieKey(name: String): Option[SenzKey] = {
      // read key from db
      val query = MongoDBObject("name" -> name)
      coll.findOne(query) match {
        case Some(obj) =>
          // have matching key
          Some(SenzKey(name, obj.getAs[String]("key").get))
        case None =>
          None
      }
    }
  }

}
