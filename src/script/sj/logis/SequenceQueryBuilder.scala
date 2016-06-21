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

object SequenceQueryBuilder extends App {
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
	strSQL.append("\nselect * from LOGIS_BACKUP.창고이동_{숫자}_세정 where 스타일 = 'RKWDLLM0221' ");

	val sql = new StringBuffer(queryBuilder(strSQL.toString, "\\{숫자\\}", "2003", "2012"))

	println(sql)

	println()
	println("--------------- " + this.getClass.getName + " 완료" + " ---------------")

}
