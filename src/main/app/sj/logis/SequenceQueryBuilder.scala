package app.sj.logis

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
		strSQL.append("\n  FROM LOGIS_BACKUP.재고조정_{숫자}_세정 ");
		strSQL.append("\n WHERE 조정원인 NOT IN ('1', '2') ");
		strSQL.append("\n   AND 창고구분 != 'X0' ");

		val sql = new StringBuffer(queryBuilder(strSQL.toString, "\\{숫자\\}", "2003", "2012"))

		println(sql)

		println()
		println("--------------- " + this.getClass.getName + " 완료" + " ---------------")
	}

}
