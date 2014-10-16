package com.decodified.scalassh

import net.schmizz.sshj.sftp.SFTPClient
import net.schmizz.sshj.xfer.scp.SCPFileTransfer
import net.schmizz.sshj.xfer.TransferListener
import net.schmizz.sshj.xfer.LoggingTransferListener

trait ScpTransferable {
  self: SshClient ⇒

  def sftp[T](fun: SFTPClient ⇒ T): Validated[T] =
    authenticatedClient.right.flatMap { client ⇒
      protect("SFTP client failed") {
        val ftpClient = client.newSFTPClient()
        try fun(ftpClient)
        finally ftpClient.close()
      }
    }

  def fileTransfer[T](fun: SCPFileTransfer ⇒ T)(implicit listener: TransferListener = new LoggingTransferListener()): Validated[T] =
    authenticatedClient.right.flatMap { client ⇒
      protect("SCP file transfer failed") {
        val transfer = client.newSCPFileTransfer()
        transfer.setTransferListener(listener)
        fun(transfer)
      }
    }

  def upload(localPath: String, remotePath: String)(implicit listener: TransferListener = new LoggingTransferListener()): Validated[Unit] =
    fileTransfer(_.upload(localPath, remotePath))(listener)

  def download(remotePath: String, localPath: String)(implicit listener: TransferListener = new LoggingTransferListener()): Validated[Unit] =
    fileTransfer(_.download(remotePath, localPath))(listener)
}
