package com.decodified

package object scalassh {
  def make[A, U](a: A)(f: A => U): A = { f(a); a }
}
