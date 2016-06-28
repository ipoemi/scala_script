package script.sj.logis

import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

import scala.collection.mutable.Queue

import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam

object SequenceQueryBuilder {
	def main(args: Array[String]) = {
		def queryBuilder(query: String, replacement: String, startYear: String, endYear: String): String = {
			val resultSQL = new StringBuffer()
			resultSQL.append(query.replaceAll(replacement, startYear.toString))
			((startYear.toInt + 1) to endYear.toInt).foreach { year =>
				resultSQL.append("\n UNION ALL " + query.replaceAll(replacement, year.toString))
			}
			resultSQL.toString
		}

		println("--------------- " + this.getClass.getName + " 시작" + " ---------------")
		println()

		val strSQL = new StringBuffer();
		strSQL.append("\nSELECT * ");
		strSQL.append("\n  FROM LOGIS_BACKUP.세트솔리드변경_{숫자}_세정 ");
		strSQL.append("\n WHERE (회사코드 ");
		strSQL.append("\n             , 센터코드 ");
		strSQL.append("\n             , 이동일자 ");
		strSQL.append("\n             , 일련순번) IN (SELECT 회사코드 ");
		strSQL.append("\n             , 센터코드 ");
		strSQL.append("\n             , 이동일자 ");
		strSQL.append("\n             , 일련순번 ");
		strSQL.append("\n          FROM LOGIS_BACKUP.세트솔리드변경_{숫자}_세정 ");
		strSQL.append("\n         GROUP BY 회사코드, 센터코드, 이동일자, 일련순번 ");
		strSQL.append("\n        HAVING COUNT(DISTINCT 창고구분) > 1) ");

		val sql = new StringBuffer(queryBuilder(strSQL.toString, "\\{숫자\\}", "2007", "2010"))

		println(sql)

		println()
		println("--------------- " + this.getClass.getName + " 완료" + " ---------------")
	}

}
