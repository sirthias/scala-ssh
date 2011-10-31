package com.decodified

package object scalassh {
  type Validated[T] = Either[String, T]

  def make[A, U](a: A)(f: A => U): A = { f(a); a }
}
