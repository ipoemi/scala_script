package app.others

import java.io.{BufferedOutputStream, File, FileOutputStream, IOException}
import java.net.{URL, URLDecoder, URLEncoder}
import java.nio.file._
import java.nio.file.attribute.BasicFileAttributes
import java.util.zip.{ZipEntry, ZipOutputStream}

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

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContextExecutor, Future}
import scala.util.{Failure, Try}

object ComicsScraper {
	val config: Config = ConfigFactory.load()
			.withValue("akka.loglevel", ConfigValueFactory.fromAnyRef("OFF"))
			.withValue("akka.stdout-loglevel", ConfigValueFactory.fromAnyRef("OFF"))
			.withValue("akka.http.host-connection-pool.client.parsing.illegal-header-warnings", ConfigValueFactory.fromAnyRef(false))
			.withValue("akka.http.host-connection-pool.max-connections", ConfigValueFactory.fromAnyRef("1"))
			.withValue("akka.http.host-connection-pool.max-open-requests", ConfigValueFactory.fromAnyRef("1024"))

	implicit val actorSystem = ActorSystem("myActorSystem", config)
	implicit val materializer = ActorMaterializer()
	implicit val executionContext: ExecutionContextExecutor = actorSystem.dispatcher

	val getUrlListFor = Map(
		"MARUMARU" -> { rootPathForGet: String =>
			val doc = JsoupBrowser().get(rootPathForGet)

			val aTagListOption = for {
				element <- doc >?> element("#vContent")
				aTagList <- element >?> elementList("a")
			} yield aTagList.toVector

			aTagListOption.getOrElse(Vector()).filter(_.attr("href").contains("archives")).map { aTag =>
				(aTag.text, aTag.attr("href"))
			}
		},
		"ZANGSISI" -> { rootPathForGet: String =>
			val doc = JsoupBrowser().get(rootPathForGet)

			val postOption = doc >?> element("#recent-post") match {
				case opt @ Some(recentPost) => opt
				case None => doc >?> element("#post")
			}

			val aTagListOption = for {
				recentPost <- postOption
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

			val postOption = doc >?> element("#recent-post") match {
				case opt @ Some(recentPost) => opt
				case None => doc >?> element("#post")
			}

			val imgTagListOption =
				if (postOption.nonEmpty) {
					for {
						post <- postOption
						contents <- post >?> element(".contents")
						imgTagList <- contents >?> elementList("img")
					} yield imgTagList.toVector
				} else {
					val mainOuterOption = doc >?> element(".main-outer")
					for {
						mainOuter <- mainOuterOption
						contents <- mainOuter >?> element(".post-body")
						imgTagList <- contents >?> elementList("img")
					} yield imgTagList.toVector
				}

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
			imgSrcList
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
		val foutTry = Try {
			new FileOutputStream(targetFile)
		}
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

	def saveFileFromUrl(url: URL, method: HttpMethod, targetFile: File): Future[Done] = {
		requestUrl(url, method).flatMap { response =>
			val statusValue = response.status.intValue()

			if (response.status == StatusCodes.OK) {
				val contentLength = response.entity.contentLengthOption.get
				if (!targetFile.exists() || targetFile.length() < contentLength) {
					if (targetFile.exists()) {
						println(s"url content length: ${contentLength}, file length: ${targetFile.length()}")
					}
					targetFile.delete()
					Await.result(saveFile(response, targetFile), Duration.Inf)
					if (!targetFile.exists() || targetFile.length() < contentLength) {
						saveFileFromUrl(url, method, targetFile)
					} else {
						Future(Done)
					}
				} else {
					Future(Done)
				}
			} else if (statusValue < 400 && statusValue >= 300) {
				val byteString = Await.result(response.entity.dataBytes.runFold(ByteString.empty)(_ ++ _).recover {
					case e: Exception => e.printStackTrace(); ByteString.empty
				}, Duration.Inf)

				val doc = JsoupBrowser().parseString(byteString.decodeString("utf-8"))
				val aTag = doc >> element("a")
				saveFileFromUrl(new URL(aTag.attr("href")), HttpMethods.GET, targetFile)
			} else {
				Future(Done)
			}
		}
	}

	def saveImgSrcList(pathSave: String, title: String, imgSrcList: Vector[String]): Future[Vector[Done]] = {
		if (imgSrcList.isEmpty) {
			Future(Vector(Done))
		} else {
			val targetDir = new File(pathSave + "/" + title)
			if (!targetDir.exists()) targetDir.mkdirs()

			Future.sequence(imgSrcList.zipWithIndex.map {
				case (src, idx) =>

					val srcUrlOption: Option[URL] = if (src.contains("proxy")) {
						val queryString = new URL(src).getQuery
						val urlStrOption = queryString.split("&").find(_.contains("url"))
						for {
							urlStr <- urlStrOption
							encodedUrl <- urlStr.split("=").lastOption
						} yield new URL(URLDecoder.decode(encodedUrl, "utf-8"))
					} else {
						Some(new URL(src))
					}.map { tmpUrl =>
						new URL(tmpUrl.getProtocol + "://" + tmpUrl.getHost + tmpUrl.getPath)
					}
					srcUrlOption match {
						case Some(srcUrl) =>
							val extension = srcUrl.getPath.substring(srcUrl.getPath.lastIndexOf("."))
							val fileName = "%05d".format(idx) + extension
							val targetFile = new File(targetDir.getAbsolutePath + "/" + fileName)
							println(s"source: ${srcUrl}, target: ${targetFile}")
							saveFileFromUrl(srcUrl, HttpMethods.GET, targetFile).recover {
								case ex: Exception =>
									ex.printStackTrace()
									Done
							}
						case None => Future {
							Done
						}
					}
			})
		}
	}

	def packDir(dir: Path): Try[Path] = {
		if (!dir.toFile.exists() || !dir.toFile.isDirectory) return Failure {
			new Exception("Directory Not Found.")
		}
		val zipFile = new File(dir.toAbsolutePath + ".zip")
		//if (zipFile.exists()) return Success(dir)
		if (zipFile.exists()) zipFile.delete()
		val fosTry = Try {
			new FileOutputStream(zipFile)
		}
		val zosTry = fosTry.flatMap { fos =>
			Try {
				new ZipOutputStream(new BufferedOutputStream(fos))
			}
		}
		println(dir)
		println(zipFile)
		val packTry = zosTry.map { zos =>
			Files.walkFileTree(dir, new SimpleFileVisitor[Path]() {
				override def visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult = {
					zos.putNextEntry(new ZipEntry(dir.relativize(file).toString))
					Files.copy(file, zos)
					zos.closeEntry()
					FileVisitResult.CONTINUE
				}

				override def preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult = {
					zos.putNextEntry(new ZipEntry(dir.relativize(dir).toString + "/"))
					zos.closeEntry()
					FileVisitResult.CONTINUE
				}
			})
		}
		packTry.recover { case e: Exception => e.printStackTrace(); e }
		zosTry.map(_.close)
		fosTry.map(_.close)
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
		val statusValue = response.status.intValue()
		if (response.status == StatusCodes.OK) {
			response.entity.dataBytes.runFold(ByteString.empty)(_ ++ _).recover {
				case e: Exception =>
					e.printStackTrace()
					ByteString.empty
			}
		} else if (statusValue < 400 && statusValue >= 300) {
			requestUrl(new URL(response.getHeader("Location").get.value), HttpMethods.POST).flatMap { response =>
				getPageContent(response)
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
				//val newTitle = title
				(newTitle, urlStr)
		} map {
			case (title, urlStr) =>
				val url = new URL(urlStr)
				val httpMethod = if (urlStr.contains("upload")) HttpMethods.GET else HttpMethods.POST
				println(s"${title}, ${url}")
				println(url)
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
					packDir(dirPath).map(deleteDir)
					doneList
				}
		}
	}

	def main(args: Array[String]): Unit = {
		println(s"--------------- ${this.getClass.getName} 시작 ---------------")
		println()

		val startTime = System.currentTimeMillis

		val rootPathForGet = "http://zangsisi.net/?p=11332"
		val rootPathForSave = "comics/악의꽃"

		val futureList = saveComics("ZANGSISI", rootPathForGet, rootPathForSave)
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