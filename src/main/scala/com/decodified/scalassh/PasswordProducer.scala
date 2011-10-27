package com.decodified.scalassh

import net.schmizz.sshj.userauth.password.{Resource, PasswordFinder}

trait PasswordProducer extends PasswordFinder

case class SimplePasswordProducer(password: String) extends PasswordProducer {
  def reqPassword(resource: Resource[_]) = password.toCharArray
  def shouldRetry(resource: Resource[_]) = false
}

object PasswordProducer {
  implicit def string2PasswordProducer(password: String) = SimplePasswordProducer(password)

  implicit def func2PasswordProducer(producer: String => String) = new PasswordProducer {
    def reqPassword(resource: Resource[_]) = producer(resource.toString).toCharArray
    def shouldRetry(resource: Resource[_]) = false
  }
}



























