package com.decodified.scalassh

import java.io.File

sealed trait SshLogin

case class PasswordLogin(user: String, passProducer: PasswordProducer) extends SshLogin

case class PublicKeyLogin(
  user: String,
  passProducer: Option[PasswordProducer],
  keyfileLocations: List[String]
) extends SshLogin

object PublicKeyLogin {
  lazy val DefaultKeyLocations = {
    val base = System.getProperty("user.home") + File.separator + ".ssh" + File.separator
    (base + "id_rsa") :: (base + "id_dsa") :: Nil
  }
  def apply(user: String): PublicKeyLogin =
    apply(user, None, DefaultKeyLocations)
  def apply(user: String, keyfileLocations: String*): PublicKeyLogin =
    PublicKeyLogin(user, None, keyfileLocations.toList)
  def apply(user: String, passProducer: PasswordProducer, keyfileLocations: List[String]): PublicKeyLogin =
    PublicKeyLogin(user, Some(passProducer), keyfileLocations)
}