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

object OldDataQueryBuilder extends App {
	println("--------------- " + this.getClass.getName + " 시작" + " ---------------")
	println()
	
	def queryBuilder(query: String, startYear: String, endYear: String): String = {
		val resultSQL = new StringBuffer()
		resultSQL.append(query.replaceAll("\\{년도\\}", startYear.toString))
		((startYear.toInt + 1) to endYear.toInt).foreach { year =>
			resultSQL.append("\n UNION ALL " + query.replaceAll("\\{년도\\}", year.toString))
		}
		resultSQL.toString
	}
	
	
	val strSQL = new StringBuffer();
	strSQL.append("\nSELECT '{년도}' 년도, COUNT(1) ") ;
	strSQL.append("\n  FROM LOGIS_BACKUP.창고입고_{년도}_세정 A ") ;
	strSQL.append("\n     , 정장SET B ") ;
	strSQL.append("\n WHERE A.입고구분 = '4' ") ;
	strSQL.append("\n   AND A.브랜드존 = B.브랜드존 ") ;
	strSQL.append("\n   AND A.브랜드 = B.브랜드 ") ;
	strSQL.append("\n   AND A.라인 = B.라인 ") ;
	strSQL.append("\n   AND A.아이템 = B.아이템 ") ;
	strSQL.append("\n   AND A.년도 = B.년도 ") ;
	strSQL.append("\n   AND A.시즌 = B.시즌 ") ;
	strSQL.append("\n   AND (A.스타일 = B.스타일 OR B.스타일 = 'z') ") ;
	
	println(queryBuilder(strSQL.toString, "1995", "2012"))
	
	println()
	println("--------------- " + this.getClass.getName + " 완료" + " ---------------")

}
