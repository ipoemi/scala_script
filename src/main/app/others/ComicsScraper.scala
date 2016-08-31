package app.others

import java.io.File
import java.io.FileOutputStream
import java.net.URL

import scala.concurrent.Future

import com.typesafe.config._

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpMethod
import akka.http.scaladsl.model.HttpMethods
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.RawHeader
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import akka.util.ByteString
import net.ruippeixotog.scalascraper.browser.JsoupBrowser
import net.ruippeixotog.scalascraper.dsl.DSL._
import net.ruippeixotog.scalascraper.dsl.DSL.Extract._
import net.ruippeixotog.scalascraper.dsl.DSL.Parse._
import java.net.URLEncoder
import scala.annotation.tailrec
import java.util.zip.ZipOutputStream
import java.io.BufferedOutputStream
import java.io.FileInputStream
import java.io.BufferedInputStream
import java.util.zip.ZipInputStream
import java.util.zip.ZipEntry
import java.nio.file.FileSystems
import java.nio.file.Paths
import java.nio.file.Files
import java.io.OutputStream
import java.nio.file.OpenOption
import java.nio.file.StandardOpenOption
import scala.util.Try
import java.nio.ByteBuffer
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import akka.Done

object ComicsScraper {
	val config = ConfigFactory.load()
		.withValue("akka.loglevel", ConfigValueFactory.fromAnyRef("OFF"))
		.withValue("akka.stdout-loglevel", ConfigValueFactory.fromAnyRef("OFF"))

	implicit val actorSystem = ActorSystem("myActorSystem", config)
	implicit val materializer = ActorMaterializer()
	implicit val executionContext = actorSystem.dispatcher

	val fileSizeThreshold = 100 * 1000
	
	val getUrlListFor = Map(
			"MARUMARU" -> { rootPathForGet: String =>
				val doc = JsoupBrowser().get(rootPathForGet)
				
				val aTagListOption = for {
					element <- doc >?> element("#vContent")
					aTagList <- element >?> elementList("a")
				} yield aTagList.toVector
				
				aTagListOption.getOrElse(Vector()).filter(_.attr("href").contains("archives")).map { aTag =>
					val newHref = aTag.attr("href").replaceAll("www.shencomics.com", "blog.yuncomics.com")
						.replaceAll("www.yuncomics.com", "blog.yuncomics.com")
					(aTag.text, newHref)
				}
			},
			"ZANGSISI" -> { rootPathForGet: String =>
				val doc = JsoupBrowser().get(rootPathForGet)
				
				val aTagListOption = for {
					recentPost <- doc >?> element("#recent-post")
					contents <- recentPost >?> element(".contents")
					aTagList <- contents >?> elementList("a")
				} yield aTagList.toVector
				
				aTagListOption.getOrElse(Vector()).map { aTag =>
					(aTag.text, aTag.attr("href"))
				}
			})
			
	val getImgSrcListFor = Map(
			"MARUMARU" -> { htmlContent: String =>
				val doc = JsoupBrowser().parseString(htmlContent)
				val imgListOption = for {
					primary <- doc >?> element("#primary")
					imgList <- primary >?> elementList("img")
				} yield imgList
				
				val imgTagList = imgListOption.getOrElse(Vector()).filter { elem =>
					val src = elem.attr("src").toLowerCase()
					val srcBoolean = src.toLowerCase.contains(".png") || src.contains(".jpg") || src.contains(".gif")
					val dataSrcBoolean = if (elem.attrs.contains("data-src")) {
						val dataSrc = elem.attr("data-src").toLowerCase
						dataSrc.toLowerCase.contains(".png") || dataSrc.contains(".jpg") || dataSrc.contains(".gif")
					} else false
					srcBoolean || dataSrcBoolean
				}
				val imgSrcList = imgTagList.map { elem =>
					if (elem.attrs.contains("data-src")) {
						elem.attr("data-src")
					} else {
						elem.attr("src")
					}
				}
				imgSrcList.toVector
			},
			"ZANGSISI" -> { htmlContent: String =>
				val doc = JsoupBrowser().parseString(htmlContent)
				
				val postOption = (doc >?> element("#recent-post")) match {
					case opt @ Some(recentPost) => opt
					case None => (doc >?> element("#post"))
				}

				val imgTagListOption = for {
					post <- postOption
					contents <- post >?> element(".contents")
					imgTagList <- contents >?> elementList("img")
				} yield imgTagList.toVector
				
				val imgTagList = imgTagListOption.getOrElse(Vector()).filter { elem =>
					val src = elem.attr("src").toLowerCase()
					val srcBoolean = src.toLowerCase.contains(".png") || src.contains(".jpg") || src.contains(".gif")
					val dataSrcBoolean = if (elem.attrs.contains("data-src")) {
						val dataSrc = elem.attr("data-src").toLowerCase
						dataSrc.toLowerCase.contains(".png") || dataSrc.contains(".jpg") || dataSrc.contains(".gif")
					} else false
					srcBoolean || dataSrcBoolean
				}
				val imgSrcList = imgTagList.map { elem =>
					if (elem.attrs.contains("data-src")) {
						elem.attr("data-src")
					} else {
						elem.attr("src")
					}
				}
				imgSrcList.toVector
			})

	def requestUrl(url: URL, method: HttpMethod): Future[HttpResponse] = {
		val connection = Http().outgoingConnection(url.getHost)
		val uri =
			(if (url.getPath == "/") "/" else url.getPath.split("/").map(URLEncoder.encode(_, "utf-8")).mkString("/")) +
			(if (url.getQuery != null) "?" + url.getQuery else "")
		val req = HttpRequest(method = method, uri = uri)
			.withHeaders(RawHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/52.0.2743.116 Safari/537.36"))
		Source.single(req).via(connection).runWith(Sink.head)
	}

	def saveFile(response: HttpResponse, targetFile: File): Future[Done] = {
		val foutTry = Try { new FileOutputStream(targetFile) }
		response.entity.dataBytes.runForeach { byteString =>
			foutTry.map(_.write(byteString.toByteBuffer.array()))
		}.recover {
			case e: Exception =>
				e.printStackTrace()
		}.map { _ =>
			foutTry.map(_.close())
			Done
		}
	}

	def saveFileToZip(response: HttpResponse, targetZipFile: File, fileName: String): Future[Done] = {
		val filesystemTry = Try { FileSystems.newFileSystem(Paths.get(targetZipFile.getAbsolutePath), null) }
		filesystemTry.recover({ case e: Exception => e.printStackTrace() })

		val writerTry = filesystemTry.flatMap { filesystem =>
			import StandardOpenOption._;
			val targetFilePath = filesystem.getPath("/" + fileName)
			Files.delete(targetFilePath)
			Files.createFile(targetFilePath)
			Try { Files.newByteChannel(targetFilePath, APPEND) }
		}

		response.entity.dataBytes.runForeach { byteString =>
			writerTry.map(_.write(byteString.toByteBuffer))
		}.recover {
			case e: Exception => e.printStackTrace()
		}.map { _ =>
			writerTry.map(_.close())
			filesystemTry.map(_.close())
			Done
		}
	}

	def saveImgSrcList(pathSave: String, title: String, imgSrcList: Vector[String]): Future[Vector[Done]] = {
		if (imgSrcList.isEmpty) {
			Future { Vector(Done) }
		} else {
			val targetDir = new File(pathSave + "/" + title.replaceAll("[^ㄱ-ㅎ가-힣0-9a-zA-Z ]", ""))
			if (!targetDir.exists()) targetDir.mkdirs()
			
			Future.sequence(imgSrcList.zipWithIndex.map {
				case (src, idx) =>
					val tmpUrl = new URL(src)
					val srcUrl = new URL(tmpUrl.getProtocol + "://" + tmpUrl.getHost + tmpUrl.getPath)
					val extension = srcUrl.getPath.substring(srcUrl.getPath.lastIndexOf("."))
					val fileName = "%010d".format(idx) + extension
					val targetFile = new File(targetDir.getAbsolutePath + "/" + fileName)
					if (!targetFile.exists() || targetFile.length() > fileSizeThreshold) {
						val responseFuture = requestUrl(srcUrl, HttpMethods.GET)
						responseFuture.flatMap { response =>
							if (response.status == StatusCodes.OK) {
								saveFile(response, targetFile)
							} else if (response.status == StatusCodes.TemporaryRedirect) {
								response.entity.dataBytes.runFold(ByteString.empty)(_ ++ _).flatMap { bString =>
									val doc = JsoupBrowser().parseString(bString.decodeString("utf-8"))
									val aTag = doc >> element("a")
									requestUrl(new URL(aTag.attr("href")), HttpMethods.GET).flatMap { response =>
										saveFile(response, targetFile)
									}
								}
							} else {
								Future { Done }
							}
						}
					} else {
						Future { Done }
					}
			})
		}
	}

	def saveComics(siteName:String, rootPathForGet: String, rootPathForSave: String): Vector[Future[Vector[Done]]] = {
		val hrefList = getUrlListFor(siteName)(rootPathForGet: String)
		hrefList.take(42).map { case (title, urlStr) =>
			val url = new URL(urlStr)
			println(s"${title}, ${url}")
			println(url)
			for {
				response <- requestUrl(url, HttpMethods.POST)
				bString <- response.entity.dataBytes.runFold(ByteString.empty)(_ ++ _)
				imgSrcList = getImgSrcListFor(siteName)(bString.decodeString("utf-8"))
				done <- saveImgSrcList(rootPathForSave, title, imgSrcList)
			} yield done
		}
	}

	def createZipFile(zipFile: File): Unit = {
		if (!zipFile.exists()) {
			Try {
				val fos = new FileOutputStream(zipFile);
				new ZipOutputStream(new BufferedOutputStream(fos));
			}.map(_.close).recover { case e: Exception => e.printStackTrace() }
		}
	}

	@tailrec
	def getLowVolumnFiles(remainFiles: Vector[File], result: Vector[File]): Vector[File] = {
		remainFiles match {
			case head +: tail =>
				if (head.isFile()) {
					if (head.length() < fileSizeThreshold)
						getLowVolumnFiles(tail, result :+ head)
					else
						getLowVolumnFiles(tail, result)
				} else if (head.isDirectory()) {
					getLowVolumnFiles(tail ++ head.listFiles(), result)
				} else {
					getLowVolumnFiles(tail, result)
				}
			case Vector() =>
				result
		}
	}

	def main(args: Array[String]): Unit = {
		println(s"--------------- ${this.getClass.getName} 시작 ---------------")
		println()

		val startTime = System.currentTimeMillis

		//val rootPathForGet = "http://marumaru.in/b/manga/84968"
		//val rootPathForSave = "comics/블리치"

		val rootPathForGet = "http://zangsisi.net/?page_id=16147"
		val rootPathForSave = "comics/드래곤볼"
		/*
		getLowVolumnFiles(Vector(new File(rootPathForSave)), Vector()).foreach { file =>
			println(s"fileName: ${file}, size: ${file.length()}")
		}
		actorSystem.terminate()
		*/
		
		val futureList = saveComics("ZANGSISI", rootPathForGet, rootPathForSave)
		Future.sequence(futureList).flatMap { _ =>
			Http().shutdownAllConnectionPools().map { _ =>
				actorSystem.terminate()
				println(s"--------------- ${this.getClass.getName} 종료 (${System.currentTimeMillis - startTime} ms ) ---------------")
			}
		}
		
	}

}