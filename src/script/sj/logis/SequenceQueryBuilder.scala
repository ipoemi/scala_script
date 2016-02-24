package script.logis

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
	println("--------------- " + this.getClass.getName + " 시작" + " ---------------")
	println()
	
	def queryBuilder(query: String, replacement:String, startYear: String, endYear: String): String = {
		val resultSQL = new StringBuffer()
		resultSQL.append(query.replaceAll(replacement, startYear.toString))
		((startYear.toInt + 1) to endYear.toInt).foreach { year =>
			resultSQL.append("\n UNION ALL " + query.replaceAll(replacement, year.toString))
		}
		resultSQL.toString
	}
	
	
	val strSQL = new StringBuffer();
	strSQL.append("\nSELECT 회사코드, 센터코드, 창고구분, 스타일, 색상, 규격 ") ;
	strSQL.append("\n     , DECODE(전표구분, 'A', DECODE(입고구분, '1', NVL(입고수량, 0), 0), 0) 생산입고 ") ;
	strSQL.append("\n     , DECODE(전표구분, 'A', DECODE(입고구분, '2', NVL(입고수량, 0), 0), 0) 이관입고 ") ;
	strSQL.append("\n     , DECODE(전표구분, 'A', DECODE(입고구분, '3', NVL(입고수량, 0), 0), 0) 완성입고 ") ;
	strSQL.append("\n     , DECODE(전표구분, 'A', DECODE(입고구분, '4', NVL(입고수량, 0), 0), 0) 이동입고 ") ;
	strSQL.append("\n     , DECODE(전표구분, 'A', 0, NVL(입고수량, 0)) 입고반품 ") ;
	strSQL.append("\n     , 0 완성출고, 0 이동출고, 0 출고수량, 0 초도출고, 0 반응출고 ") ;
	strSQL.append("\n     , 0 교환출고, 0 수주출고, 0 기타출고, 0 수불출고, 0반품수량 ") ;
	strSQL.append("\n     , 0 시즌반품, 0 불량반품, 0 수주반품, 0 교환반품, 0 수정반품 ") ;
	strSQL.append("\n     , 0 수선반품, 0 기타반품, 0 수불반품, 0 재고조정, 0 작업지시 ") ;
	strSQL.append("\n     , 0 재고수량, 0 LOSS수량 ") ;
	strSQL.append("\n     , 브랜드존, 브랜드, 라인, 아이템, 년도, 시즌 ") ;
	strSQL.append("\n  FROM LOGIS_BACKUP.창고입고_제품_{숫자}_세정 ") ;
	
	val sql = new StringBuffer(queryBuilder(strSQL.toString, "\\{숫자\\}", "1995", "2012"))
	sql.append("\n UNION ALL ")
	sql.append("\nSELECT 회사코드, 센터코드, 창고구분, 스타일, 색상, 규격 ") ;
	sql.append("\n     , DECODE(전표구분, 'A', DECODE(입고구분, '1', NVL(입고수량, 0), 0), 0) 생산입고 ") ;
	sql.append("\n     , DECODE(전표구분, 'A', DECODE(입고구분, '2', NVL(입고수량, 0), 0), 0) 이관입고 ") ;
	sql.append("\n     , DECODE(전표구분, 'A', DECODE(입고구분, '3', NVL(입고수량, 0), 0), 0) 완성입고 ") ;
	sql.append("\n     , DECODE(전표구분, 'A', DECODE(입고구분, '4', NVL(입고수량, 0), 0), 0) 이동입고 ") ;
	sql.append("\n     , DECODE(전표구분, 'A', 0, NVL(입고수량, 0)) 입고반품 ") ;
	sql.append("\n     , 0 완성출고, 0 이동출고, 0 출고수량, 0 초도출고, 0 반응출고 ") ;
	sql.append("\n     , 0 교환출고, 0 수주출고, 0 기타출고, 0 수불출고, 0반품수량 ") ;
	sql.append("\n     , 0 시즌반품, 0 불량반품, 0 수주반품, 0 교환반품, 0 수정반품 ") ;
	sql.append("\n     , 0 수선반품, 0 기타반품, 0 수불반품, 0 재고조정, 0 작업지시 ") ;
	sql.append("\n     , 0 재고수량, 0 LOSS수량 ") ;
	sql.append("\n     , 브랜드존, 브랜드, 라인, 아이템, 년도, 시즌 ") ;
	sql.append("\n  FROM 창고입고_제품 ") ;
	
	println(sql)
	
	println()
	println("--------------- " + this.getClass.getName + " 완료" + " ---------------")

}
