package com.decodified.scalassh

import java.net.InetAddress
import net.schmizz.sshj.transport.verification.{HostKeyVerifier => HKV}
import java.security.PublicKey
import net.schmizz.sshj.common.SecurityUtils

case class SshEndpoint(address: InetAddress, port: Int, hostKeyVerifier: HKV)

object SshEndpoint {
  def apply(host: String): SshEndpoint = apply(host, 22)
  def apply(host: String, port: Int): SshEndpoint = apply(InetAddress.getByName(host), port, HostKeyVerifier.Default)
  def apply(host: String, fingerprint: String): SshEndpoint = apply(host, fingerprint, 22)
  def apply(host: String, fingerprint: String, port: Int): SshEndpoint =
    apply(InetAddress.getByName(host), port, HostKeyVerifier.forFingerprint(fingerprint))
}

object HostKeyVerifier {
  lazy val Default = new HKV {
    def verify(hostname: String, port: Int, key: PublicKey) = true
  }
  def forFingerprint(fingerprint: String) = new HKV {
    def verify(hostname: String, port: Int, key: PublicKey) = SecurityUtils.getFingerprint(key) == fingerprint
  }
}





























