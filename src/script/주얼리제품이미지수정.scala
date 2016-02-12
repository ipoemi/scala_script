package script

import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

import scala.collection.mutable.Queue

import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam

object 주얼리제품이미지수정 extends App {

	def getCroppedImage(source: BufferedImage, tolerance: Double, marginRatio: Double): BufferedImage = {
		// Get our top-left pixel color as our "baseline" for cropping
		val baseColor = Color.WHITE.getRGB

		val width = source.getWidth();
		val height = source.getHeight();

		var (topY, topX) = (Integer.MAX_VALUE, Integer.MAX_VALUE);
		var (bottomY, bottomX) = (-1, -1);

		var startTime = System.currentTimeMillis()

		(0 to height - 1).foreach { y =>
			(0 to width - 1).foreach { x =>
				if (colorWithinTolerance(baseColor, source.getRGB(x, y), tolerance)) {
					if (x < topX) topX = x;
					if (y < topY) topY = y;
					if (x > bottomX) bottomX = x;
					if (y > bottomY) bottomY = y;
				}
			}
		}

		println("take time1: " + (System.currentTimeMillis() - startTime))

		val marginX = ((bottomX - topX + 1) * marginRatio).asInstanceOf[Int]
		val marginY = ((bottomY - topY + 1) * marginRatio).asInstanceOf[Int]

		val newWidth = (bottomX - topX + 1) + marginX * 2
		val newHeight = (bottomY - topY + 1) + marginY * 2

		val destination = new BufferedImage(newWidth, newHeight, source.getType);

		destination.getGraphics.setColor(Color.WHITE)
		destination.getGraphics.fillRect(0, 0, newWidth, newHeight)
		destination.getGraphics.drawImage(
			source, marginX, marginY, destination.getWidth() - marginX, destination.getHeight() - marginY,
			topX, topY, bottomX, bottomY, null);

		destination;
	}

	def colorWithinTolerance(a: Int, b: Int, tolerance: Double): Boolean = {
		val aAlpha = ((a & 0xFF000000) >>> 24); // Alpha level
		val aRed = ((a & 0x00FF0000) >>> 16); // Red level
		val aGreen = ((a & 0x0000FF00) >>> 8); // Green level
		val aBlue = (a & 0x000000FF); // Blue level

		val bAlpha = ((b & 0xFF000000) >>> 24); // Alpha level
		val bRed = ((b & 0x00FF0000) >>> 16); // Red level
		val bGreen = ((b & 0x0000FF00) >>> 8); // Green level
		val bBlue = (b & 0x000000FF); // Blue level

		val distance = Math.sqrt(
			(aAlpha - bAlpha) * (aAlpha - bAlpha)
				+ (aRed - bRed) * (aRed - bRed)
				+ (aGreen - bGreen) * (aGreen - bGreen)
				+ (aBlue - bBlue) * (aBlue - bBlue));

		// 510.0 is the maximum distance between two colors 
		// (0,0,0,0 -> 255,255,255,255)
		val percentAway = distance / 510.0d;

		(percentAway > tolerance);
	}

	val sep = System.getProperty("file.separator")

	/*
	val driver = "oracle.jdbc.driver.OracleDriver"
	val url = "jdbc:oracle:thin:@115.88.29.16:1521:ora11g"

	var connection: Connection = null

	val strSQL = new StringBuilder;
	strSQL.append("\nSELECT DISTINCT A.스타일 ") ;
	strSQL.append("\n FROM 상_스타일 A ") ;
	strSQL.append("\n , 상_스타일전개구분 B ") ;
	strSQL.append("\n WHERE A.스타일 = B.스타일 ") ;
	strSQL.append("\n AND B.매장형태구분 = '8' ") ;
	
	val resultFile = new File("result.txt")
	val bw = new BufferedWriter(new FileWriter(resultFile))
	try {
		
		// make the connection
		Class.forName(driver)
		connection = DriverManager.getConnection(url, "market_j", "market_j")

		// create the statement, and run the select query
		val statement = connection.createStatement()
		val result = statement.executeQuery(strSQL.toString)
		
		val buffer: ListBuffer[Map[String, String]] = new ListBuffer
		while (result.next()) {
			buffer += Map("스타일" -> result.getString(1))
		}
		
		buffer.foreach { x =>
			val imgFile = new File("제품사진" + sep + x("스타일").substring(0, 3) + sep + x("스타일") + ".jpg")
			if (!imgFile.exists()) {
				bw.write(x("스타일") + "\r\n")
			}
		}

	} catch {
		case e: Throwable => e.printStackTrace
	} finally {
		bw.close()
		connection.close()
	}
	*/

	val fileQueue = new Queue[File]
	val path = "D:\\Works\\Develop\\Scala\\scala_script\\제품사진"
	fileQueue.enqueue(new File(path))
	while (!fileQueue.isEmpty) {
		val inFile = fileQueue.dequeue()
		if (inFile.isDirectory()) {
			inFile.listFiles().foreach { x => fileQueue.enqueue(x) }
		} else {
			println(inFile.getAbsolutePath)

			val orgFilePath = inFile.getAbsolutePath
			val noExtFilePath = orgFilePath.substring(0, orgFilePath.lastIndexOf("."))

			val fileToRename = new File(noExtFilePath + "_ORG.jpg")
			inFile.renameTo(fileToRename)

			val fileToCompressed = new File(orgFilePath)

			val is = new FileInputStream(fileToRename)
			//val is = new FileInputStream(inFile);
			val os = new FileOutputStream(fileToCompressed)

			val quality = 0.5f;

			// create a BufferedImage as the result of decoding the supplied InputStream
			val marginRatio = fileToRename.getName.substring(3, 4) match {
				case "E" => 0.3
				case "R" => 0.3
				case _ => 0.0
			}
			val image = getCroppedImage(ImageIO.read(is), 0.05, marginRatio);

			// get all image writers for JPG format
			val writers = ImageIO.getImageWritersByFormatName("jpg");

			if (!writers.hasNext()) throw new IllegalStateException("No writers found");

			val writer = writers.next();
			val ios = ImageIO.createImageOutputStream(os);
			writer.setOutput(ios);

			val param = writer.getDefaultWriteParam();

			// compress to a given quality
			param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
			param.setCompressionQuality(quality);

			// appends a complete image stream containing a single image and
			//associated stream and image metadata and thumbnails to the output
			writer.write(null, new IIOImage(image, null, null), param);

			// close all streams
			is.close();
			os.close();
			ios.close();
			writer.dispose();

			fileToRename.delete()
		}
	}

	/*
	val curFile = new File("제품사진")
	curFile.list().foreach { x =>
		val orgImgFile = new File("제품사진" + sep + x)
		if (orgImgFile.isFile()) {
			val imgFolderFile = new File("제품사진" + sep + x.substring(0, 3))
			if (!imgFolderFile.exists()) imgFolderFile.mkdir()
			val newImgFile = new File("제품사진" + sep + x.substring(0, 3) + sep + x.substring(0, 11) + ".jpg")
			if (!newImgFile.exists())
				orgImgFile.renameTo(newImgFile)
			else
				orgImgFile.delete()
		}
	}
	*/

}
