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
import java.nio.file.SimpleFileVisitor
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.io.IOException
import java.nio.file.FileVisitResult
import scala.util.Failure
import scala.util.Success

object ComicsScraper {
	val config = ConfigFactory.load()
		.withValue("akka.loglevel", ConfigValueFactory.fromAnyRef("OFF"))
		.withValue("akka.stdout-loglevel", ConfigValueFactory.fromAnyRef("OFF"))
		.withValue("akka.http.host-connection-pool.max-connections", ConfigValueFactory.fromAnyRef("2"))
		.withValue("akka.http.host-connection-pool.max-open-requests", ConfigValueFactory.fromAnyRef("1024"))

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
			println(htmlContent)
			val doc = JsoupBrowser().parseString(htmlContent)

			val postOption = (doc >?> element("#recent-post")) match {
				case opt @ Some(recentPost) => opt
				case None => (doc >?> element("#post"))
			}

			val imgTagListOption =
				if (postOption.nonEmpty) {
					for {
						post <- postOption
						contents <- post >?> element(".contents")
						imgTagList <- contents >?> elementList("img")
					} yield imgTagList.toVector
				} else {
					val mainOuterOption = (doc >?> element(".main-outer"))
					println(s"mainOuter: ${mainOuterOption}")
					for {
						mainOuter <- mainOuterOption 
						contents <- mainOuter >?> element(".post-body")
						imgTagList <- contents >?> elementList("img")
					} yield imgTagList.toVector
				}
			
			println(s"size: ${imgTagListOption.getOrElse(Vector()).size}")

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
		val fileSystemTry = Try { FileSystems.newFileSystem(Paths.get(targetZipFile.getAbsolutePath), null) }

		val writerTry = fileSystemTry.map { fileSystem =>
			import StandardOpenOption._;
			val targetFilePath = fileSystem.getPath("/" + fileName)
			if (Files.exists(targetFilePath)) Files.delete(targetFilePath)
			Files.createFile(targetFilePath)
			Files.newByteChannel(targetFilePath, APPEND)
		}

		writerTry.recover { case e: Exception => e.printStackTrace(); Done }

		response.entity.dataBytes.runForeach { byteString =>
			writerTry.map(_.write(byteString.toByteBuffer))
		}.recover {
			case e: Exception => e.printStackTrace()
		}.map { _ =>
			writerTry.map(_.close())
			fileSystemTry.map(_.close())
			Done
		}
	}

	def saveImgSrcList(pathSave: String, title: String, imgSrcList: Vector[String]): Future[Vector[Done]] = {
		if (imgSrcList.isEmpty) {
			Future { Vector(Done) }
		} else {
			val targetDir = new File(pathSave + "/" + title)
			if (!targetDir.exists()) targetDir.mkdirs()

			Future.sequence(imgSrcList.zipWithIndex.map {
				case (src, idx) =>
					val tmpUrl = new URL(src)
					val srcUrl = new URL(tmpUrl.getProtocol + "://" + tmpUrl.getHost + tmpUrl.getPath)
					val extension = srcUrl.getPath.substring(srcUrl.getPath.lastIndexOf("."))
					val fileName = "%05d".format(idx) + extension
					val targetFile = new File(targetDir.getAbsolutePath + "/" + fileName)
					val future = if (!targetFile.exists() || targetFile.length() < fileSizeThreshold) {
						if (targetFile.exists()) targetFile.delete()
						println(s"source: ${srcUrl}, target: ${targetFile}")
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
					//Await.result(future, Duration.Inf)
					future
			})
		}
	}

	def packDir(dir: Path): Try[Path] = {
		if (!dir.toFile.exists() || !dir.toFile.isDirectory()) return Failure { new Exception("Directory Not Found.") }
		val zipFile = new File(dir.toAbsolutePath + ".zip")
		if (zipFile.exists()) return Success(dir)
		val fosTry = Try { new FileOutputStream(zipFile) }
		val zosTry = fosTry.flatMap { fos => Try { new ZipOutputStream(new BufferedOutputStream(fos)) } };
		println(dir)
		println(zipFile)
		val packTry = zosTry.map { zos =>
			Files.walkFileTree(dir, new SimpleFileVisitor[Path]() {
				override def visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult = {
					zos.putNextEntry(new ZipEntry(dir.relativize(file).toString()));
					Files.copy(file, zos);
					zos.closeEntry();
					FileVisitResult.CONTINUE
				}

				override def preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult = {
					zos.putNextEntry(new ZipEntry(dir.relativize(dir).toString() + "/"));
					zos.closeEntry();
					FileVisitResult.CONTINUE
				}
			})
		}
		zosTry.map(_.close).recover { case e: Exception => e.printStackTrace() }
		fosTry.map(_.close).recover { case e: Exception => e.printStackTrace() }
		packTry
	}
	
	def deleteDir(dir: Path): Try[Path] = {
		Try {
			Files.walkFileTree(dir, new SimpleFileVisitor[Path]() {
				override def visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult = {
					Files.deleteIfExists(file)
					FileVisitResult.CONTINUE
				}

				override def preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult = {
					FileVisitResult.CONTINUE
				}

				override def postVisitDirectory(dir: Path, ioe: IOException): FileVisitResult = {
					Files.deleteIfExists(dir)
					FileVisitResult.CONTINUE
				}
				
			})
		}
	}
	
	def getPageContent(response: HttpResponse): Future[ByteString] = {
		if (response.status == StatusCodes.OK) {
			response.entity.dataBytes.runFold(ByteString.empty)(_ ++ _)
		} else if (response.status == StatusCodes.TemporaryRedirect) {
			response.entity.dataBytes.runFold(ByteString.empty)(_ ++ _).flatMap { bString =>
				val doc = JsoupBrowser().parseString(bString.decodeString("utf-8"))
				val aTag = doc >> element("a")
				requestUrl(new URL(aTag.attr("href")), HttpMethods.GET).flatMap { response =>
					response.entity.dataBytes.runFold(ByteString.empty)(_ ++ _)
				}
			}
		} else {
			Future(ByteString.empty)
		}
		
	}

	def saveComics(siteName: String, rootPathForGet: String, rootPathForSave: String): Vector[Future[Vector[Done]]] = {
		val hrefList = getUrlListFor(siteName)(rootPathForGet: String)
		hrefList.zipWithIndex.map {
			case ((title, urlStr), idx) =>
				val newTitle = s"${"%03d".format(idx + 1)}.${title.replaceAll("[^ㄱ-ㅎ가-힣0-9a-zA-Z.\\-~ ]", "")}"
				(newTitle, urlStr)
		}.drop(10).take(1).map {
			case (title, urlStr) =>
				val url = new URL(urlStr)
				println(s"${title}, ${url}")
				println(url)
				val httpMethod = if (urlStr.contains("upload")) HttpMethods.GET else HttpMethods.POST
				println(httpMethod)
				val future = for {
					response <- requestUrl(url, httpMethod)
					bString <- getPageContent(response) 
					imgSrcList = getImgSrcListFor(siteName)(bString.decodeString("utf-8"))
					done <- saveImgSrcList(rootPathForSave, title, imgSrcList)
				} yield done
				Await.result(future, Duration.Inf)
				future.map { doneList =>
					val dirPath = Paths.get(rootPathForSave + "/" + title)
					packDir(dirPath).map(deleteDir(_))
					doneList
				}
		}
	}

	/*
	def createZipFile(zipFile: File): Unit = {
		if (!zipFile.exists()) {
			val fosTry = Try { new FileOutputStream(zipFile) }
			val zosTry = fosTry.flatMap { fos => Try { new ZipOutputStream(new BufferedOutputStream(fos)) } };
			zosTry.map(_.close).recover { case e: Exception => e.printStackTrace() }
			fosTry.map(_.close).recover { case e: Exception => e.printStackTrace() }
		}
	}

	def saveImgSrcListToZip(pathSave: String, title: String, imgSrcList: Vector[String]): Future[Vector[Done]] = {
		if (imgSrcList.isEmpty) {
			Future { Vector(Done) }
		} else {
			val targetZipFile = new File(pathSave + "/" + title.replaceAll("[^ㄱ-ㅎ가-힣0-9a-zA-Z.\\- ]", "") + ".zip")
			if (!targetZipFile.exists()) createZipFile(targetZipFile)
			
			Future.sequence(imgSrcList.zipWithIndex.map {
				case (src, idx) =>
					val tmpUrl = new URL(src)
					val srcUrl = new URL(tmpUrl.getProtocol + "://" + tmpUrl.getHost + tmpUrl.getPath)
					val extension = srcUrl.getPath.substring(srcUrl.getPath.lastIndexOf("."))
					val fileName = "%05d".format(idx) + extension
					
					val zipFileSystemTry = Try { FileSystems.newFileSystem(Paths.get(targetZipFile.getAbsolutePath), null) }
					val filePathTry = zipFileSystemTry.map(_.getPath("/" + fileName))
					val fileInfoTry = filePathTry.map { filePath =>
						(Files.exists(filePath), if (Files.exists(filePath)) Files.size(filePath) else 0L)
					}
					val fileInfo = fileInfoTry.getOrElse((true, Long.MaxValue))
					fileInfoTry.recover { case e: Exception => e.printStackTrace(); Done }
					zipFileSystemTry.map(_.close())

					val future = if (!fileInfo._1 || fileInfo._2 < fileSizeThreshold) {
						println(s"source: ${srcUrl}, target: ${targetZipFile}${filePathTry.get}")
						println(s"file exists: ${fileInfo._1}, file size: ${fileInfo._2}")
						val responseFuture = requestUrl(srcUrl, HttpMethods.GET)
						responseFuture.flatMap { response =>
							if (response.status == StatusCodes.OK) {
								saveFileToZip(response, targetZipFile, fileName)
							} else if (response.status == StatusCodes.TemporaryRedirect) {
								response.entity.dataBytes.runFold(ByteString.empty)(_ ++ _).flatMap { bString =>
									val doc = JsoupBrowser().parseString(bString.decodeString("utf-8"))
									val aTag = doc >> element("a")
									requestUrl(new URL(aTag.attr("href")), HttpMethods.GET).flatMap { response =>
										saveFileToZip(response, targetZipFile, fileName)
									}
								}
							} else {
								Future { Done }
							}
						}
					} else {
						Future { Done }
					}
					Await.result(future, Duration.Inf)
					future
			})
		}
	}

	def saveComicsToZip(siteName:String, rootPathForGet: String, rootPathForSave: String): Vector[Future[Vector[Done]]] = {
		val hrefList = getUrlListFor(siteName)(rootPathForGet: String)
		hrefList.take(5).zipWithIndex.map { case ((title, urlStr), idx) =>
			val url = new URL(urlStr)
			println(s"${title}, ${url}")
			println(url)
			for {
				response <- requestUrl(url, HttpMethods.POST)
				bString <- response.entity.dataBytes.runFold(ByteString.empty)(_ ++ _)
				imgSrcList = getImgSrcListFor(siteName)(bString.decodeString("utf-8"))
				done <- saveImgSrcListToZip(rootPathForSave, s"${"%03d".format(idx + 1)}.${title}", imgSrcList)
			} yield done
		}
	}
	*/

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

		val rootPathForGet = "http://zangsisi.net/?page_id=2613"
		val rootPathForSave = "comics/히스토리에"
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