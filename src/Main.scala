import java.sql.DriverManager
import scala.collection.mutable.ListBuffer
import java.sql.Connection
import java.io.File
import java.io.FileWriter
import java.io.BufferedWriter

object Main {

	private val driver = "oracle.jdbc.driver.OracleDriver"
	private val url = "jdbc:oracle:thin:@115.88.29.16:1521:ora11g"
	
	val sep = System.getProperty("file.separator")

	def main(args: Array[String]): Unit = {
		var connection: Connection = null

		val strSQL = new StringBuilder;
		strSQL.append("\nSELECT DISTINCT A.스타일 ") ;
		strSQL.append("\n  FROM 상_스타일 A ") ;
		strSQL.append("\n     , 상_스타일전개구분 B ") ;
		strSQL.append("\n WHERE A.스타일 = B.스타일 ") ;
		strSQL.append("\n   AND B.매장형태구분 = '8' ") ;
		
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
				//println(x.substring(0, 11) + ".jpg")
				//println(orgImgFile.toString + " : " + newImgFile.toString)
			}
		}
		*/
		
	}
}
