package com.score.senzswitch.crypto

import java.io.{File, FileInputStream, FileOutputStream}
import java.security._
import java.security.spec.{PKCS8EncodedKeySpec, X509EncodedKeySpec}
import javax.crypto.Cipher

import com.score.senzswitch.config.AppConfig
import sun.misc.{BASE64Decoder, BASE64Encoder}

object RSAUtils extends AppConfig {
  def initRSAKeys() = {
    // first create .keys directory
    val dir: File = new File(keysDir)
    if (!dir.exists) {
      dir.mkdir
    }

    // generate keys if not exists
    val filePublicKey = new File(publicKeyLocation)
    if (!filePublicKey.exists) {
      generateRSAKeyPair()
    }
  }

  def generateRSAKeyPair() = {
    // generate key pair
    val keyPairGenerator = KeyPairGenerator.getInstance("RSA")
    keyPairGenerator.initialize(1024, new SecureRandom)
    val keyPair: KeyPair = keyPairGenerator.generateKeyPair

    // save public key
    val x509keySpec = new X509EncodedKeySpec(keyPair.getPublic.getEncoded)
    val publicKeyStream = new FileOutputStream(publicKeyLocation)
    publicKeyStream.write(x509keySpec.getEncoded)

    // save private key
    val pkcs8KeySpec = new PKCS8EncodedKeySpec(keyPair.getPrivate.getEncoded)
    val privateKeyStream = new FileOutputStream(privateKeyLocation)
    privateKeyStream.write(pkcs8KeySpec.getEncoded)
  }

  def loadRSAKeyPair() = {
    // read public key
    val filePublicKey = new File(publicKeyLocation)
    var inputStream = new FileInputStream(publicKeyLocation)
    val encodedPublicKey: Array[Byte] = new Array[Byte](filePublicKey.length.toInt)
    inputStream.read(encodedPublicKey)
    inputStream.close

    // read private key
    val filePrivateKey = new File(privateKeyLocation)
    inputStream = new FileInputStream(privateKeyLocation)
    val encodedPrivateKey: Array[Byte] = new Array[Byte](filePrivateKey.length.toInt)
    inputStream.read(encodedPrivateKey)
    inputStream.close

    val keyFactory: KeyFactory = KeyFactory.getInstance("RSA")

    // public key
    val publicKeySpec: X509EncodedKeySpec = new X509EncodedKeySpec(encodedPublicKey)
    val publicKey: PublicKey = keyFactory.generatePublic(publicKeySpec)

    // private key
    val privateKeySpec: PKCS8EncodedKeySpec = new PKCS8EncodedKeySpec(encodedPrivateKey)
    val privateKey: PrivateKey = keyFactory.generatePrivate(privateKeySpec)

    new KeyPair(publicKey, privateKey)
  }

  def loadRSAPublicKey() = {
    // get public key via key pair
    val keyPair = loadRSAKeyPair()
    val publicKeyStream = keyPair.getPublic.getEncoded

    // BASE64 encoded string
    new BASE64Encoder().encode(publicKeyStream).replaceAll("\n", "").replaceAll("\r", "")
  }

  def signSenz(payload: String) = {
    // get private key via key pair
    val keyPair = loadRSAKeyPair()
    val privateKey = keyPair.getPrivate

    // sign the payload
    val signature: Signature = Signature.getInstance("SHA256withRSA")
    signature.initSign(privateKey)
    signature.update(payload.getBytes)

    // signature as Base64 encoded string
    new BASE64Encoder().encode(signature.sign).replaceAll("\n", "").replaceAll("\r", "")
  }

  def verifySenzSignature(payload: String, signedPayload: String) = {
    // get public key via key pair
    val keyPair = loadRSAKeyPair()
    val publicKey = keyPair.getPublic

    val signature = Signature.getInstance("SHA256withRSA")
    signature.initVerify(publicKey)
    signature.update(payload.getBytes)

    // decode(BASE64) signed payload and verify signature
    signature.verify(new BASE64Decoder().decodeBuffer(signedPayload))
  }

  def encrypt(payload: String, publicKey: PublicKey) = {
    val cipher: Cipher = Cipher.getInstance("RSA")
    cipher.init(Cipher.ENCRYPT_MODE, publicKey)

    cipher.doFinal(payload.getBytes)
  }

  def decrypt(payload: Array[Byte], privateKey: PrivateKey) = {
    val cipher: Cipher = Cipher.getInstance("RSA")
    cipher.init(Cipher.DECRYPT_MODE, privateKey)
    new String(cipher.doFinal(payload))
  }

  def verify(pubKey: String, payload: String, signedPayload: String) = {
    // public key
    val keyFactory: KeyFactory = KeyFactory.getInstance("RSA")
    val publicKeySpec: X509EncodedKeySpec = new X509EncodedKeySpec(new BASE64Decoder().decodeBuffer(pubKey))
    val publicKey: PublicKey = keyFactory.generatePublic(publicKeySpec)

    // verify signature
    val signature = Signature.getInstance("SHA256withRSA")
    signature.initVerify(publicKey)
    signature.update(payload.getBytes)

    // decode(BASE64) signed payload and verify signature
    signature.verify(new BASE64Decoder().decodeBuffer(signedPayload))
  }

  def sign(privateKey: String, payload: String) = {
    val keyFactory: KeyFactory = KeyFactory.getInstance("RSA")
    val publicKeySpec: X509EncodedKeySpec = new X509EncodedKeySpec(new BASE64Decoder().decodeBuffer(privateKey))
//    val publicKey: PublicKey = keyFactory.generatePublic(publicKeySpec)
//
//    // sign the payload
//    val signature: Signature = Signature.getInstance("SHA256withRSA")
//    signature.initSign(privateKey)
//    signature.update(payload.getBytes)
//
//    // signature as Base64 encoded string
//    new BASE64Encoder().encode(signature.sign).replaceAll("\n", "").replaceAll("\r", "")
  }

}
