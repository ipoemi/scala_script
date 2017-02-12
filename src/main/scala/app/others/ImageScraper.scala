package app.others

import java.io.{File, FileOutputStream}
import java.net.{URL, URLEncoder}

import akka.Done
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers._
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

  var cookies: Seq[HttpCookie] = Seq()

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

  val getImgSrcsFor: Map[String, (String) => Seq[(String, String)]] = Map(
    "H-HENTAI" -> { htmlContent: String =>
      val doc = JsoupBrowser().parseString(htmlContent)

      val nameOpt: Option[Seq[String]] = for {
        sni <- doc >?> element(".sni")
        divs <- doc >?> elementList(".sn + div")
      } yield divs.map(_.text.split(" ")(0))

      val imgsOpt: Option[Seq[String]] = for {
        sni <- doc >?> element(".sni")
        imgs <- sni >?> elementList("img[style]")
      } yield imgs.map(_.attr("src"))

      nameOpt.getOrElse(Seq[String]()).zip(imgsOpt.getOrElse(Seq[String]()))
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

  def requestUrl(url: URL, method: HttpMethod, headers: List[HttpHeader] = List(), entity: RequestEntity = HttpEntity.Empty): Future[HttpResponse] = {
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
    val defaultHeaders = if (headers.isEmpty)
      List(RawHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/52.0.2743.116 Safari/537.36"))
    else
      headers
    val req = HttpRequest(method = method, uri = uriBuilder.toString, entity = entity, headers = defaultHeaders)
    //println(req.headers)
    //println(req.entity)
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

  def savePage(siteName: String, title: String, pathForSave: String, url: URL, method: HttpMethod, headers: List[HttpHeader]): Future[Seq[Done]] = {
    val imgContainersFt = getItems(getImgContainersFor(siteName), url, method, headers)
    imgContainersFt.flatMap { imgContainers =>
      imgContainers.foreach(c => println(s"container: $c"))
      Future.sequence(imgContainers.zipWithIndex.flatMap { case (container, idx) =>
        val newUrl = pathToUrl(container, url)
        val items = Await.result(getItems(getImgSrcsFor(siteName), newUrl, method, headers), Duration.Inf).map(i => (i._1, i._2, newUrl))
        val imgUrls = items.map(i => (i._1, pathToUrl(i._2, i._3)))
        imgUrls.map { case (fileName, imgUrl) =>
          val targetDir = new File(pathForSave)
          val targetFile = new File(targetDir.getAbsolutePath + "/" + fileName)
          if (!targetFile.exists()) {
            println(s"target: ${targetFile}, source: ${imgUrl}")
            saveFileFromUrl(imgUrl, targetFile, method)
          } else {
            Future(Done)
          }
        }
      })
    }
  }

  def getItems[T](getter: (String) => Seq[T], url: URL, method: HttpMethod, headers: List[HttpHeader]): Future[Seq[T]] = {
    for {
      response <- requestUrl(url, method, headers)
      content <- getContent(response, url, method)
      items = getter(content)
    } yield items
  }

  def saveImages(siteName: String, rootPathForGet: String, rootPathForSave: String, headers: List[HttpHeader]): Seq[Future[Seq[Done]]] = {
    val targetDir = new File(rootPathForSave)
    if (!targetDir.exists()) targetDir.mkdirs()
    val rootUrl = new URL(rootPathForGet)
    val pages = Await.result(getItems(getPagesFor(siteName), rootUrl, HttpMethods.GET, headers), Duration.Inf)
    pages.foreach(println)
    pages.map(page => savePage(siteName, page._1, rootPathForSave, pathToUrl(page._2, rootUrl), HttpMethods.GET, headers))
  }

  def getRegisteredHeader(url: URL): (List[HttpHeader], String) = {
    val resp = Await.result(requestUrl(url, HttpMethods.GET), Duration.Inf)
    val cookies = resp.headers.collect {
      case c: `Set-Cookie` => c.cookie
    }
    println(cookies.head.name + "=" + cookies.head.value.split(";").head)

    val headers = List(
      RawHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8"),
      RawHeader("Accept-Encoding", "gzip, deflate"),
      RawHeader("Accept-Language", "ko-KR,ko;q=0.8,en-US;q=0.6,en;q=0.4"),
      RawHeader("Content-Type", "application/x-www-form-urlencoded"),
      RawHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/56.0.2924.87 Safari/537.36"),
      Host("www.firewallproxy.ga"),
      Origin("http://www.firewallproxy.ga"),
      Referer("http://www.firewallproxy.ga/"),
      RawHeader("Upgrade-Insecure-Request", "1"),
      RawHeader("DNT", "1"),
      Cookie(cookies.head.name, cookies.head.value)
      //RawHeader("Cookie", cookies.head.name + "=" + cookies.head.value.split(";").head)
    )
    val formData = FormData("u" -> "https%3A%2F%2Fe-hentai.org%2Fs%2Faf0f080f87%2F158177-187",
      "encodeURL" -> "on",
      "allowCookies" -> "on",
      "stripJS" -> "on",
      "stripObjects" -> "on")
    val response = Await.result(requestUrl(new URL("http://www.firewallproxy.ga/includes/process.php?action=update"), HttpMethods.POST, headers, formData.toEntity), Duration.Inf)
    //println(response.status.intValue)
    println(response.getHeader("Location").get.value)
    (List(
      RawHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/56.0.2924.87 Safari/537.36"),
      Cookie(cookies.head.name, cookies.head.value)
    ), response.getHeader("Location").get.value)
  }

  def main(args: Array[String]): Unit = {

    println(s"--------------- ${this.getClass.getName} 시작 ---------------")
    println()

    val startTime = System.currentTimeMillis

    val rootPathForGet = "https://e-hentai.org/g/158177/b8b64ffba7/"
    //val rootPathForGet = "http://newproxy.ninja/index.php?q=aHR0cHM6Ly9lLWhlbnRhaS5vcmcvZy8xNTgxNzcvYjhiNjRmZmJhNy8%3D"
    //val rootPathForGet = "http://neweb.gq/index.php?q=aHR0cHM6Ly9lLWhlbnRhaS5vcmcvZy8xNTgxNzcvYjhiNjRmZmJhNy8%3D"
    //val rootPathForGet = "http://www.freewebproxyserver.pw/index.php?q=aHR0cHM6Ly9lLWhlbnRhaS5vcmcvZy8xNTgxNzcvYjhiNjRmZmJhNy8%2FcD00"
    //val rootPathForGet = "http://www.firewallproxy.ga/browse.php?u=LXb2y2kKFXAkB%2BBvmpGvq7ZLMb6J8OzHsq3L7oRDZ6s%2FQ1prLFHYbw%3D%3D&b=29&f=norefer"
    val proxyServerPath = "http://www.firewallproxy.ga/"
    val rootPathForSave = "comics/드나4"

    val (headers, location) = getRegisteredHeader(new URL(proxyServerPath))

    val futureList = saveImages("H-HENTAI", rootPathForGet, rootPathForSave, headers)
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