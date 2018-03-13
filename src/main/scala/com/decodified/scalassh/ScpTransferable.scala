/*
 * Copyright 2011-2018 Mathias Doenitz
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.decodified.scalassh

import net.schmizz.sshj.common.LoggerFactory
import net.schmizz.sshj.sftp.SFTPClient
import net.schmizz.sshj.xfer.scp.SCPFileTransfer
import net.schmizz.sshj.xfer.TransferListener
import net.schmizz.sshj.xfer.LoggingTransferListener

abstract class ScpTransferable {
  self: SshClient ⇒

  def sftp[T](fun: SFTPClient ⇒ T): Validated[T] =
    authenticatedClient.right.flatMap { client ⇒
      protect("SFTP client failed") {
        val ftpClient = client.newSFTPClient()
        try fun(ftpClient)
        finally ftpClient.close()
      }
    }

  def fileTransfer[T](fun: SCPFileTransfer ⇒ T)(implicit l: TransferListener = defaultListener): Validated[T] =
    authenticatedClient.right.flatMap { client ⇒
      protect("SCP file transfer failed") {
        val transfer = client.newSCPFileTransfer()
        transfer.setTransferListener(l)
        fun(transfer)
      }
    }

  def upload(localPath: String, remotePath: String)(implicit l: TransferListener = defaultListener): Validated[Unit] =
    fileTransfer(_.upload(localPath, remotePath))(l)

  def download(remotePath: String, localPath: String)(implicit l: TransferListener = defaultListener): Validated[Unit] =
    fileTransfer(_.download(remotePath, localPath))(l)

  private def defaultListener = new LoggingTransferListener(LoggerFactory.DEFAULT)
}
