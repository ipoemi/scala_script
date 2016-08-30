package script.others

import java.io.File
import java.io.FileOutputStream
import java.net.URL

import scala.concurrent.Future

import com.typesafe.config._

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpMethod
import akka.http.scaladsl.model.HttpMethods._
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
import akka.Done
import scala.concurrent.Await
import scala.concurrent.duration.Duration

object ComicsScraper {
	val config = ConfigFactory.load()
		.withValue("akka.loglevel", ConfigValueFactory.fromAnyRef("OFF"))
		.withValue("akka.stdout-loglevel", ConfigValueFactory.fromAnyRef("OFF"))

	implicit val actorSystem = ActorSystem("myActorSystem", config)
	implicit val materializer = ActorMaterializer()
	implicit val executionContext = actorSystem.dispatcher

	val fileSizeThreshold = 100 * 1000

	def requestUrl(url: URL, method: HttpMethod): Future[HttpResponse] = {
		val connection = Http().outgoingConnection(url.getHost)
		val req = HttpRequest(method = method, uri = url.getPath.split("/").map(URLEncoder.encode(_, "utf-8")).mkString("/"))
			.withHeaders(RawHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/52.0.2743.116 Safari/537.36"))
		Source.single(req).via(connection).runWith(Sink.head)
	}

	def saveFile(targetDir: File, fileName: String, fileUrl: URL): Future[Option[HttpResponse]] = {
		val targetFile = new File(targetDir.getAbsolutePath + "/" + fileName)
		if (targetFile.exists && targetFile.length() < fileSizeThreshold) targetFile.delete()
		if (!targetFile.exists) {
			println(targetFile.getAbsoluteFile)
			println(fileUrl)
			requestUrl(fileUrl, HttpMethods.GET).flatMap { response =>
				if (response.status == StatusCodes.OK) {
					val fout = new FileOutputStream(targetFile)
					response.entity.dataBytes.runForeach { byteString =>
						fout.write(byteString.toByteBuffer.array())
					}.recover {
						case e: Exception =>
					}.map {
						_ =>
							fout.close()
							Some(response)
					}
				} else {
					Future.successful(Some(response))
				}
			}
		} else {
			Future { None }
		}
	}

	def saveFileToZip(targetZipFile: File, fileName: String, fileUrl: URL): Future[Option[HttpResponse]] = {
		val fsTry = Try { FileSystems.newFileSystem(Paths.get(targetZipFile.getAbsolutePath), null) }
		fsTry.recover({ case e: Exception => e.printStackTrace() })

		fsTry.map { fs =>
			import StandardOpenOption._;
			val targetFilePath = fs.getPath("/" + fileName)
			if (Files.exists(targetFilePath) && Files.size(targetFilePath) < fileSizeThreshold)
				Files.delete(targetFilePath)
			if (!Files.exists(targetFilePath)) {
				println(targetFilePath)
				println(fileUrl)
				requestUrl(fileUrl, HttpMethods.GET).flatMap { response =>
					if (response.status == StatusCodes.OK) {
						Files.createFile(targetFilePath)
						val writerTry = Try { Files.newByteChannel(targetFilePath, APPEND) }
						val ftTry = writerTry.map { writer =>
							response.entity.dataBytes.runForeach { byteString =>
								writer.write(byteString.toByteBuffer)
							} map { _ =>
								writerTry.map(_.close)
								fsTry.map(_.close)
								Some(response)
							}
						}
						ftTry.recover({ case e: Exception => e.printStackTrace() })
						ftTry.getOrElse(Future { None })
					} else {
						Future { None }
					}
				}
			} else {
				Future { None }
			}
		}.getOrElse(Future { None })
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
	
	def getUrlList(rootPathForGet: String): Vector[String] = {
		val browser = JsoupBrowser()
		val doc = browser.get(rootPathForGet)

		val hrefList = doc >> element("#vContent") >> attrs("href")("a")
		hrefList.toVector.filter(_.contains("archives")).map { href =>
			href.replaceAll("www.shencomics.com", "blog.yuncomics.com")
				.replaceAll("www.yuncomics.com", "blog.yuncomics.com")
		}
	}

	def saveComics(rootPathForGet: String, rootPathForSave: String): Vector[Future[Any]] = {
		strRootPath
		val hrefList = getUrlList(rootPathForGet: String)
		hrefList.map { urlStr =>
			val url = new URL(urlStr)
			requestUrl(url, HttpMethods.POST).flatMap { response =>
				response.entity.dataBytes.runFold(ByteString(""))(_ ++ _).flatMap { bString =>
					val doc = browser.parseString(bString.decodeString("utf-8"))
					val targetDir = new File(rootPathForSave + "/" + "%03d".format(doc.title.replaceAll("\\D", "").toInt))
					if (!targetDir.exists()) targetDir.mkdirs()
					val imgTagList = (doc >> element("#primary") >> elementList("img")).filter { elem =>
						val src = elem.attr("src").toLowerCase()
						val srcBoolean = src.toLowerCase.contains(".png") || src.contains(".jpg") || src.contains(".gif")
						val dataSrcBoolean = if (elem.attrs.contains("data-src")) {
							val dataSrc = elem.attr("data-src").toLowerCase
							dataSrc.toLowerCase.contains(".png") || dataSrc.contains(".jpg") || dataSrc.contains(".gif")
						} else false
						srcBoolean || dataSrcBoolean
					}
					val hrefList = imgTagList.map { elem =>
						if (elem.attrs.contains("data-src")) {
							elem.attr("data-src")
						} else {
							elem.attr("src")
						}
					}
					Future.sequence(hrefList.zipWithIndex.map {
						case (href, idx) =>
							val tmpUrl = new URL(href)
							val fileUrl = new URL(tmpUrl.getProtocol + "://" + tmpUrl.getHost + tmpUrl.getPath)
							val extension = fileUrl.getPath.substring(fileUrl.getPath.lastIndexOf("."))
							//val fileName = fileUrl.getPath.substring(fileUrl.getPath.lastIndexOf('/') + 1)
							val fileName = "%010d".format(idx) + extension
							val responseFt = saveFile(targetDir, fileName, fileUrl)
							responseFt.flatMap {
								case Some(response) =>
									if (response.status == StatusCodes.TemporaryRedirect) {
										response.entity.dataBytes.runFold(ByteString.empty)(_ ++ _).map { bString =>
											val doc = browser.parseString(bString.decodeString("utf-8"))
											val aTag = doc >> element("a")
											saveFile(targetDir, fileName, new URL(aTag.attr("href")))
										}
									} else {
										Future { None }
									}
								case None => Future { None }
							}
					})
				}
			}
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

	def saveComicsToZip(rootPathForGet: String, rootPathForSave: String): Vector[Future[Any]] = {
		val hrefList = getUrlList(rootPathForGet: String)
		hrefList.take(1).map { urlStr =>
			val url = new URL(urlStr)
			requestUrl(url, HttpMethods.POST).flatMap { response =>
				response.entity.dataBytes.runFold(ByteString(""))(_ ++ _).flatMap { bString =>
					val doc = browser.parseString(bString.decodeString("utf-8"))
					val targetZipFile = new File(rootPathForSave + "/" + "%03d".format(doc.title.replaceAll("\\D", "").toInt) + ".zip")
					createZipFile(targetZipFile)
					val imgTagList = (doc >> element("#primary") >> elementList("img")).filter { elem =>
						val src = elem.attr("src").toLowerCase()
						val srcBoolean = src.toLowerCase.contains(".png") || src.contains(".jpg") || src.contains(".gif")
						val dataSrcBoolean = if (elem.attrs.contains("data-src")) {
							val dataSrc = elem.attr("data-src").toLowerCase
							dataSrc.toLowerCase.contains(".png") || dataSrc.contains(".jpg") || dataSrc.contains(".gif")
						} else false
						srcBoolean || dataSrcBoolean
					}
					val hrefList = imgTagList.map { elem =>
						if (elem.attrs.contains("data-src")) {
							elem.attr("data-src")
						} else {
							elem.attr("src")
						}
					}
					Future.sequence(hrefList.zipWithIndex.map {
						case (href, idx) =>
							val tmpUrl = new URL(href)
							val fileUrl = new URL(tmpUrl.getProtocol + "://" + tmpUrl.getHost + tmpUrl.getPath)
							val extension = fileUrl.getPath.substring(fileUrl.getPath.lastIndexOf("."))
							//val fileName = fileUrl.getPath.substring(fileUrl.getPath.lastIndexOf('/') + 1)
							val fileName = "%010d".format(idx) + extension
							val responseFt = saveFileToZip(targetZipFile, fileName, fileUrl)
							val ft = responseFt.flatMap {
								case Some(response) =>
									if (response.status == StatusCodes.TemporaryRedirect) {
										response.entity.dataBytes.runFold(ByteString.empty)(_ ++ _).map { bString =>
											val doc = browser.parseString(bString.decodeString("utf-8"))
											val aTag = doc >> element("a")
											saveFileToZip(targetZipFile, fileName, new URL(aTag.attr("href")))
										}
									} else {
										Future { None }
									}
								case None => Future { None }
							}
							Await.result(responseFt, Duration.Inf)
							ft
					})
				}
			}
		}
	}

	def main(args: Array[String]): Unit = {
		println(s"--------------- ${this.getClass.getName} 시작 ---------------")
		println()

		val startTime = System.currentTimeMillis

		val rootPathForGet = "http://marumaru.in/b/manga/84968"
		val rootPathForSave = "comics/블리치"

		/*
		getLowVolumnFiles(Vector(new File(rootPathForSave)), Vector()).foreach { file =>
			println(s"fileName: ${file}, size: ${file.length()}")
		}
		actorSystem.terminate()
		*/
		
		val futureList = saveComicsToZip(rootPathForGet, rootPathForSave)
		Future.sequence(futureList).map { _ =>
			Http().shutdownAllConnectionPools().onComplete { _ =>
				actorSystem.terminate()
				println(s"--------------- ${this.getClass.getName} 종료 (${System.currentTimeMillis - startTime} ms ) ---------------")
			}
		}
	}

}