package io.github.morgaroth.telegram.bot.bots.boobsbot

import java.io.{File => JFile, FileOutputStream}
import java.nio.file.Files
import java.security.MessageDigest
import javax.xml.bind.DatatypeConverter

import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import akka.util.Timeout
import spray.client.pipelining._
import spray.http.{HttpHeader, ContentType}
import spray.http.HttpHeaders.`Content-Type`
import spray.http.MediaTypes._

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Random

/**
  * Created by mateusz on 17.10.15.
  */
object FetchAndCalculateHash {

  case class UnsupportedBoobsContent(other: ContentType) extends IllegalArgumentException

  case object NoContentInformation extends IllegalArgumentException

  def apply(link: String)(implicit as: ActorSystem, log: LoggingAdapter): Future[(String, JFile, String)] = {
    //    println(s"hashing file from $link")
    import as.dispatcher
    implicit val tm: Timeout = 2.minutes
    val pipe = sendReceive
    pipe(Get(link)).flatMap { res =>
      val contentType = res.headers.find(_.name == `Content-Type`.name) match {
        case Some(`Content-Type`(ContentType(`image/gif`, _))) => Future.successful("gif")
        case Some(`Content-Type`(ContentType(`image/jpeg`, _))) => Future.successful("jpeg")
        case Some(`Content-Type`(ContentType(`video/mp4`, _))) => Future.successful("mp4")
        case Some(`Content-Type`(ContentType(`image/png`, _))) => Future.successful("png")
        case Some(`Content-Type`(ContentType(`image/x-ms-bmp`, _))) => Future.successful("bmp")
        case Some(`Content-Type`(other)) => Future.failed(UnsupportedBoobsContent(other))
        case None => Future.failed(NoContentInformation)
      }
      contentType.map { ct =>
        log.info(s"recovered content $ct")
        val data = res.entity.data.toByteArray
        val tmpDir = Files.createTempDirectory("tmpdir")
        val tmpFile = new JFile(tmpDir.toFile, s"boobs.$ct")
        tmpFile.createNewFile()
        // val tmpFile = JFile.createTempFile(Random.alphanumeric.take(5).mkString("boobs", "", ""), s".$ct")
        val stream = new FileOutputStream(tmpFile)
        stream.write(data)
        stream.flush()
        stream.close()
        val hash = calculateMD5(tmpFile)
        (hash, tmpFile, ct)
      }
    }
  }

  def calculateMD5(f: Array[Byte]): String = {
    val md = MessageDigest.getInstance("MD5")
    md.update(f)
    DatatypeConverter.printHexBinary(md.digest())
  }

  def calculateMD5(f: JFile): String = {
    import better.files.Cmds.md5
    import better.files.{File => BFile}
    md5(BFile(f.getAbsolutePath))
  }

}