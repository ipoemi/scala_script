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
	println("--------------- " + this.getClass.getName + " 시작" + " ---------------")
	println()
	
	throw new Exception("""""")

	def queryBuilder(query: String, replacement:String, startYear: String, endYear: String): String = {
		val resultSQL = new StringBuffer()
		resultSQL.append(query.replaceAll(replacement, startYear.toString))
		((startYear.toInt + 1) to endYear.toInt).foreach { year =>
			resultSQL.append("\n UNION ALL " + query.replaceAll(replacement, year.toString))
		}
		resultSQL.toString
	}
	
	
	val strSQL = new StringBuffer();
	strSQL.append("\nSELECT A.회사코드, A.센터코드, A.창고구분, A.스타일, A.색상, A.규격 ") ;
	strSQL.append("\n     , 0 생산입고, 0 이관입고, 0 완성입고 ") ;
	strSQL.append("\n     , DECODE(이동구분,'A',NVL(이동수량,0),0) 이동입고 ") ;
	strSQL.append("\n     , 0 입고반품, 0 완성출고 ") ;
	strSQL.append("\n     , DECODE(이동구분,'B',NVL(이동수량,0),0) 이동출고 ") ;
	strSQL.append("\n     , 0 출고수량, 0 초도출고, 0 반응출고 ") ;
	strSQL.append("\n     , 0 교환출고, 0 수주출고, 0 기타출고, 0 수불출고, 0 반품수량 ") ;
	strSQL.append("\n     , 0 시즌반품, 0 불량반품, 0 수주반품, 0 교환반품, 0 수정반품 ") ;
	strSQL.append("\n     , 0 수선반품, 0 기타반품, 0 수불반품, 0 재고조정, 0 작업지시 ") ;
	strSQL.append("\n     , 0 재고수량, 0 LOSS수량 ") ;
	strSQL.append("\n     , NVL(B.브랜드존, A.브랜드존) 브랜드존 ") ;
	strSQL.append("\n     , NVL(B.브랜드, A.브랜드) 브랜드 ") ;
	strSQL.append("\n     , B.라인 ") ;
	strSQL.append("\n     , NVL(B.아이템, A.아이템) 아이템 ") ;
	strSQL.append("\n     , NVL(B.년도, A.년도) 년도 ") ;
	strSQL.append("\n     , NVL(B.시즌, A.시즌) 시즌 ") ;
	strSQL.append("\n  FROM LOGIS_BACKUP.아소트솔리드변경_{숫자}_세정 A ") ;
	strSQL.append("\n     , 스타일 B ") ;
	strSQL.append("\n WHERE A.회사코드 = B.회사코드 (+) ") ;
	strSQL.append("\n   AND A.스타일 = B.스타일 (+) ") ;
	
	val sql = new StringBuffer(queryBuilder(strSQL.toString, "\\{숫자\\}", "2003", "2012"))
	sql.append("\n UNION ALL ")
	sql.append("\nSELECT A.회사코드, A.센터코드, A.창고구분, A.스타일, A.색상, A.규격 ") ;
	sql.append("\n     , 0 생산입고, 0 이관입고, 0 완성입고 ") ;
	sql.append("\n     , DECODE(이동구분,'A',NVL(이동수량,0),0) 이동입고 ") ;
	sql.append("\n     , 0 입고반품, 0 완성출고 ") ;
	sql.append("\n     , DECODE(이동구분,'B',NVL(이동수량,0),0) 이동출고 ") ;
	sql.append("\n     , 0 출고수량, 0 초도출고, 0 반응출고 ") ;
	sql.append("\n     , 0 교환출고, 0 수주출고, 0 기타출고, 0 수불출고, 0 반품수량 ") ;
	sql.append("\n     , 0 시즌반품, 0 불량반품, 0 수주반품, 0 교환반품, 0 수정반품 ") ;
	sql.append("\n     , 0 수선반품, 0 기타반품, 0 수불반품, 0 재고조정, 0 작업지시 ") ;
	sql.append("\n     , 0 재고수량, 0 LOSS수량 ") ;
	sql.append("\n     , NVL(B.브랜드존, A.브랜드존) 브랜드존 ") ;
	sql.append("\n     , NVL(B.브랜드, A.브랜드) 브랜드 ") ;
	sql.append("\n     , B.라인 ") ;
	sql.append("\n     , NVL(B.아이템, A.아이템) 아이템 ") ;
	sql.append("\n     , NVL(B.년도, A.년도) 년도 ") ;
	sql.append("\n     , NVL(B.시즌, A.시즌) 시즌 ") ;
	sql.append("\n  FROM 아소트솔리드변경 A ") ;
	sql.append("\n     , 스타일 B ") ;
	sql.append("\n WHERE A.회사코드 = B.회사코드 (+) ") ;
	sql.append("\n   AND A.스타일 = B.스타일 (+) ") ;
	
	println(sql)
	
	println()
	println("--------------- " + this.getClass.getName + " 완료" + " ---------------")

}
