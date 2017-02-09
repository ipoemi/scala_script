package app.others

import java.io.{File, FileOutputStream}
import java.net.{URL, URLEncoder}

import akka.Done
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.RawHeader
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString
import com.typesafe.config._
import net.ruippeixotog.scalascraper.browser.JsoupBrowser
import net.ruippeixotog.scalascraper.dsl.DSL.Extract._
import net.ruippeixotog.scalascraper.dsl.DSL._
import net.ruippeixotog.scalascraper.model.Element

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContextExecutor, Future}
import scala.util.Try

object ImageScraper {
  val config: Config = ConfigFactory.load()
    .withValue("akka.loglevel", ConfigValueFactory.fromAnyRef("OFF"))
    .withValue("akka.stdout-loglevel", ConfigValueFactory.fromAnyRef("OFF"))
    .withValue("akka.http.host-connection-pool.client.parsing.illegal-header-warnings", ConfigValueFactory.fromAnyRef(false))
    .withValue("akka.http.host-connection-pool.max-connections", ConfigValueFactory.fromAnyRef("1"))
    .withValue("akka.http.host-connection-pool.max-open-requests", ConfigValueFactory.fromAnyRef("1024"))

  implicit val actorSystem = ActorSystem("myActorSystem", config)
  implicit val materializer = ActorMaterializer()
  implicit val executionContext: ExecutionContextExecutor = actorSystem.dispatcher

  val getPagesFor: Map[String, (String) => Seq[(String, String)]] = Map(
    "H-HENTAI" -> { htmlContent: String =>
      //println(htmlContent)
      val doc = JsoupBrowser().parseString(htmlContent)

      val pagesOpt: Option[Seq[Element]] = for {
        gtb <- doc >?> element(".gtb")
        ptt <- gtb >?> element(".ptt")
        as <- ptt >?> elementList("a")
      } yield as

      pagesOpt.getOrElse(Seq[Element]()).filter(_.text != ">").map(a => (("000" + a.text).takeRight(3), a.attr("href")))
    })

  val getImgContainersFor: Map[String, (String) => Seq[String]] = Map(
    "H-HENTAI" -> { htmlContent: String =>
      val doc = JsoupBrowser().parseString(htmlContent)

      val imgContainersOpt: Option[Seq[Element]] = for {
        gdt <- doc >?> element("#gdt")
        as <- gdt >?> elementList("a")
      } yield as

      imgContainersOpt.getOrElse(Seq[Element]()).map(_.attr("href"))
    })

  val getImgSrcsFor: Map[String, (String) => Seq[String]] = Map(
    "H-HENTAI" -> { htmlContent: String =>
      val doc = JsoupBrowser().parseString(htmlContent)

      val imgsOpt: Option[Seq[Element]] = for {
        sni <- doc >?> element(".sni")
        imgs <- sni >?> elementList("img[style]")
      } yield imgs

      imgsOpt.getOrElse(Seq[Element]()).map(_.attr("src"))
    })

  def pathToUrl(toPath: String, fromUrl: URL): URL = {
    val protocol = fromUrl.getProtocol
    val host = fromUrl.getHost
    val port = fromUrl.getPort
    val fromPath = fromUrl.getPath

    if (toPath.startsWith("http")) new URL(toPath)
    else if (toPath.startsWith("/")) new URL(s"$protocol://$host:$port$toPath")
    else new URL(s"$protocol://$host:$port$fromPath/$toPath")
  }

  def requestUrl(url: URL, method: HttpMethod): Future[HttpResponse] = {
    val connection =
      if (url.getProtocol == "https") Http().outgoingConnectionHttps(url.getHost)
      else Http().outgoingConnection(url.getHost)
    val uriBuilder = new StringBuilder
    if (url.getPath == null || url.getPath == "") uriBuilder.append("/")
    else {
      uriBuilder.append(url.getPath.split("/").map(URLEncoder.encode(_, "utf-8")).mkString("/"))
      if (url.getPath.last == '/') uriBuilder.append("/")
    }
    if (url.getQuery != null) uriBuilder.append("?" + url.getQuery)
    val req = HttpRequest(method = method, uri = uriBuilder.toString)
      .withHeaders(RawHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/52.0.2743.116 Safari/537.36"))
    Source.single(req).via(connection).runWith(Sink.head)
  }

  def saveFileToDisk(response: HttpResponse, targetFile: File): Future[Done] = {
    val foutTry = Try {
      new FileOutputStream(targetFile)
    }
    val result = response.entity.dataBytes.runForeach { byteString =>
      foutTry.map(_.write(byteString.toByteBuffer.array()))
    }
    result.onComplete { _ =>
      foutTry.map(_.close())
    }
    result
  }

  def retry[T](response: HttpResponse, fromUrl: URL, method: HttpMethod)(body: (HttpResponse) => (Future[T])): Future[T] = {
    val statusValue = response.status.intValue()
    if (response.status == StatusCodes.OK) {
      body(response)
    } else if (statusValue < 400 && statusValue >= 300) {
      val newPath = response.getHeader("Location").get.value
      requestUrl(pathToUrl(newPath, fromUrl), method).flatMap { res =>
        body(res)
      }
    } else {
      Future.failed(new Exception(s"URL: $fromUrl Error Ocurred"))
    }
  }


  def saveFileFromUrl(url: URL, targetFile: File, method: HttpMethod): Future[Done] = {
    requestUrl(url, method).flatMap { response =>
      retry(response, url, method) { res =>
        val contentLength = response.entity.contentLengthOption.get
        if (!targetFile.exists() || targetFile.length() < contentLength) {
          if (targetFile.exists()) {
            println(s"url content length: ${contentLength}, file length: ${targetFile.length()}")
          }
          targetFile.delete()
          Await.result(saveFileToDisk(response, targetFile), Duration.Inf)
          if (!targetFile.exists() || targetFile.length() < contentLength) {
            saveFileFromUrl(url, targetFile, method)
          } else {
            Future(Done)
          }
        } else {
          Future(Done)
        }
      }.recover {
        case e: Exception =>
          e.printStackTrace()
          new Exception(s"targetFile: $targetFile").printStackTrace()
          Done
      }
    }
  }

  def getContent(response: HttpResponse, fromUrl: URL, method: HttpMethod): Future[String] = {
    retry(response, fromUrl, method) { res =>
      res.entity.dataBytes.runFold(ByteString.empty)(_ ++ _).map { bs => bs.decodeString("utf-8") }
    }.recover {
      case e: Exception =>
        e.printStackTrace()
        ""
    }
  }

  def savePage(siteName: String, title: String, pathForSave: String, url: URL, method: HttpMethod): Future[Seq[Done]] = {
    val imgContainersFt = getItems(getImgContainersFor(siteName), url, method)
    imgContainersFt.flatMap { imgContainers =>
      imgContainers.foreach(c => println(s"container: $c"))
      Future.sequence(imgContainers.zipWithIndex.flatMap { case (container, idx) =>
        val newUrl = pathToUrl(container, url)
        val srcAndUrls = Await.result(getItems(getImgSrcsFor(siteName), newUrl, method), Duration.Inf).map((_, newUrl))
        val imgUrls = srcAndUrls.map(src => pathToUrl(src._1, src._2))
        imgUrls.map { imgUrl =>
          val targetDir = new File(pathForSave)
          val extension = ".jpg"
          val fileName = title + "_" + "%05d".format(idx + 1) + extension
          val targetFile = new File(targetDir.getAbsolutePath + "/" + fileName)
          println(s"target: ${targetFile}, source: ${imgUrl}")
          saveFileFromUrl(imgUrl, targetFile, method)
        }
      })
    }
  }

  def getItems[T](getter: (String) => Seq[T], url: URL, method: HttpMethod): Future[Seq[T]] = {
    for {
      response <- requestUrl(url, method)
      content <- getContent(response, url, method)
      items = getter(content)
    } yield items
  }

  def saveImages(siteName: String, rootPathForGet: String, rootPathForSave: String): Seq[Future[Seq[Done]]] = {
    val targetDir = new File(rootPathForSave)
    if (!targetDir.exists()) targetDir.mkdirs()
    val rootUrl = new URL(rootPathForGet)
    val pages = Await.result(getItems(getPagesFor(siteName), rootUrl, HttpMethods.GET), Duration.Inf)
    pages.foreach(println)
    pages.map(page => savePage(siteName, page._1, rootPathForSave, pathToUrl(page._2, rootUrl), HttpMethods.GET))
  }

  def main(args: Array[String]): Unit = {

    println(s"--------------- ${this.getClass.getName} 시작 ---------------")
    println()

    val startTime = System.currentTimeMillis

    val rootPathForGet = "http://newproxy.ninja/index.php?q=aHR0cHM6Ly9lLWhlbnRhaS5vcmcvZy8xNTgxNzcvYjhiNjRmZmJhNy8%3D"
    val rootPathForSave = "comics/드나4"

    val futureList = saveImages("H-HENTAI", rootPathForGet, rootPathForSave)
    Future.sequence(futureList).recover {
      case e: Exception =>
        e.printStackTrace()
    }.flatMap { _ =>
      Http().shutdownAllConnectionPools().map { _ =>
        actorSystem.terminate()
        println(s"--------------- ${this.getClass.getName} 종료 (${System.currentTimeMillis - startTime} ms ) ---------------")
      }
    }
  }

}