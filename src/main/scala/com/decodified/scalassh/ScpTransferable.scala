package com.decodified.scalassh

import net.schmizz.sshj.sftp.SFTPClient
import net.schmizz.sshj.xfer.scp.SCPFileTransfer
import net.schmizz.sshj.xfer.TransferListener
import net.schmizz.sshj.xfer.LoggingTransferListener

trait ScpTransferable {
  self: SshClient ⇒

  def sftp[T](fun: SFTPClient ⇒ T) = {
    authenticatedClient.right.flatMap {
      client ⇒
        startSession(client).right.flatMap {
          session ⇒
            protect("SFTP client failed") {
              val ftpClient = client.newSFTPClient()
              try {
                fun(ftpClient)
              } finally {
                ftpClient.close()
              }
            }
        }
    }
  }

  def fileTransfer(fun: SCPFileTransfer ⇒ Unit)(implicit listener: TransferListener = new LoggingTransferListener()) = {
    authenticatedClient.right.flatMap {
      client ⇒
        startSession(client).right.flatMap {
          session ⇒
            protect("SCP file transfer failed") {
              val transfer = client.newSCPFileTransfer()
              transfer.setTransferListener(listener)
              fun(transfer)
              this
            }
        }
    }
  }

  def upload(localPath: String, remotePath: String)(implicit listener: TransferListener = new LoggingTransferListener()) = {
    fileTransfer(_.upload(localPath, remotePath))(listener)
  }

  def download(remotePath: String, localPath: String)(implicit listener: TransferListener = new LoggingTransferListener()) = {
    fileTransfer(_.download(remotePath, localPath))(listener)
  }
}
