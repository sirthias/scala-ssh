package com.decodified.scalassh

object SSH {
  def apply[T, R](host: String, configProvider: HostConfigProvider = HostFileConfig())
                 (body: SshClient => T)(implicit wrapper: ResultWrapper[T, R]): Validated[R] = {
    SshClient(host, configProvider).right.flatMap { client =>
      val result = {
        try { wrapper(body(client)) }
        catch { case e: Exception => Left(e.toString) }
      }
      client.close()
      result
    }
  }
}

abstract class ResultWrapper[T, R] extends (T => Validated[R])

object ResultWrapper extends Implicits

private[scalassh] class Implicits {
  implicit def eitherWrapper[T, R](implicit ev: T <:< Validated[R]) = new ResultWrapper[T, R] {
    def apply(value: T) = value
  }
}

private[scalassh] class LowerPriorityImplicits {
  implicit def wrapper[T] = new ResultWrapper[T, T] {
    def apply(value: T) = Right(value)
  }
}