package app.sj21.logis

import java.io.FileOutputStream

import java.sql.DriverManager

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.Duration
import scala.language.implicitConversions
import scala.util.Try

import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.xssf.usermodel.XSSFWorkbook

import com.util.db.DBUtil
import scala.concurrent.Promise

object 물류센터총재고현황 {
	def main(args: Array[String]) = {

		def getDbConnection() = {
			Try {
				val driver = "oracle.jdbc.driver.OracleDriver"
				val url = "jdbc:oracle:thin:@210.108.224.7:1521:ORA10g"
				val username = "logis_sj"
				val password = "logis_sj"
				Class.forName(driver)
				val dbCon = Some(DriverManager.getConnection(url, username, password))
				dbCon.get.setAutoCommit(false)
				dbCon
			}.get
		}

		val strSQL = new StringBuffer();
		strSQL.append("\nSELECT ");
		strSQL.append("\n (CASE WHEN 구분 = '2' THEN '서창2센터' ");
		strSQL.append("\n   WHEN 구분 = '5' THEN  '이관센터' ");
		strSQL.append("\n   WHEN 구분 = '6' THEN '검품센터' ");
		strSQL.append("\n   WHEN 구분 = 'I' THEN '특판센터' ");
		strSQL.append("\n   WHEN 구분 = '4' THEN 'NSP센터' ");
		strSQL.append("\n ELSE (SELECT 코드명 FROM 기초코드 WHERE 코드구분 = '040' AND 코드 = 구분 AND 회사코드 = '2') END) AS \"구분명\", ");
		strSQL.append("\n SUM(행거창고) AS \"행거창고\", SUM(평지이관창고) AS \"평지이관창고\", ");
		strSQL.append("\n SUM(파레트랙창고) AS \"파레트랙창고\", SUM(샘플창고) AS \"샘플창고\", SUM(행사창고) AS \"행사창고\", ");
		strSQL.append("\n SUM(반품창고) AS \"반품창고\", SUM(수불창고) AS \"수불창고\", SUM(완성장) AS \"완성장\", ");
		strSQL.append("\n SUM(불량창고) AS \"불량창고\", SUM(총재고) AS \"총재고\", ");
		strSQL.append("\n SUM(행거창고금액) AS \"행거창고금액\", SUM(평지이관창고금액) AS \"평지이관창고금액\", ");
		strSQL.append("\n SUM(파레트랙창고금액) AS \"파레트랙창고금액\", SUM(샘플창고금액) AS \"샘플창고금액\", SUM(행사창고금액) AS \"행사창고금액\", ");
		strSQL.append("\n SUM(반품창고금액) AS \"반품창고금액\", SUM(수불창고금액) AS \"수불창고금액\", SUM(완성장금액) AS \"완성장금액\", ");
		strSQL.append("\n SUM(불량창고금액) AS \"불량창고금액\", SUM(총재고금액) AS \"총재고금액\" ");
		strSQL.append("\nFROM( ");
		strSQL.append("\n SELECT 센터코드 AS \"구분\", ");
		strSQL.append("\n   SUM(CASE WHEN 창고구분 IN ('21','22') THEN NVL(당월재고,0) ELSE 0 END) AS \"행거창고\", ");
		strSQL.append("\n   SUM(CASE WHEN 창고구분 IN ('31', '32', '81', '82') THEN NVL(당월재고,0) ELSE 0 END) AS \"평지이관창고\", ");
		strSQL.append("\n   SUM(CASE WHEN 창고구분 = '91' THEN NVL(당월재고,0) ELSE 0 END) AS \"파레트랙창고\", ");
		strSQL.append("\n   SUM(CASE WHEN 창고구분 = '51' THEN NVL(당월재고,0) ELSE 0 END) AS \"샘플창고\", ");
		strSQL.append("\n   SUM(CASE WHEN 창고구분 IN ('61', '62') THEN NVL(당월재고,0) ELSE 0 END) AS \"행사창고\", ");
		strSQL.append("\n   SUM(CASE WHEN 창고구분 IN ('54', '58', '59') THEN NVL(당월재고,0) ELSE 0 END) AS \"반품창고\", ");
		strSQL.append("\n   SUM(CASE WHEN 창고구분 = '71' THEN NVL(당월재고,0) ELSE 0 END) AS \"수불창고\", ");
		strSQL.append("\n   0 AS \"완성장\", ");
		strSQL.append("\n   0 AS \"불량창고\", ");
		strSQL.append("\n   SUM(NVL(당월재고, 0)) AS \"총재고\", ");
		strSQL.append("\n   SUM(CASE WHEN 창고구분 IN ('21','22') THEN NVL(당월재고,0) ELSE 0 END * F_TAGPRICE(회사코드, 스타일)) AS \"행거창고금액\", ");
		strSQL.append("\n   SUM(CASE WHEN 창고구분 IN ('31', '32', '81', '82') THEN NVL(당월재고,0) ELSE 0 END * F_TAGPRICE(회사코드, 스타일)) AS \"평지이관창고금액\", ");
		strSQL.append("\n   SUM(CASE WHEN 창고구분 = '91' THEN NVL(당월재고,0) ELSE 0 END * F_TAGPRICE(회사코드, 스타일)) AS \"파레트랙창고금액\", ");
		strSQL.append("\n   SUM(CASE WHEN 창고구분 = '51' THEN NVL(당월재고,0) ELSE 0 END * F_TAGPRICE(회사코드, 스타일)) AS \"샘플창고금액\", ");
		strSQL.append("\n   SUM(CASE WHEN 창고구분 IN ('61', '62') THEN NVL(당월재고,0) ELSE 0 END * F_TAGPRICE(회사코드, 스타일)) AS \"행사창고금액\", ");
		strSQL.append("\n   SUM(CASE WHEN 창고구분 IN ('54', '58', '59') THEN NVL(당월재고,0) ELSE 0 END * F_TAGPRICE(회사코드, 스타일)) AS \"반품창고금액\", ");
		strSQL.append("\n   SUM(CASE WHEN 창고구분 = '71' THEN NVL(당월재고,0) ELSE 0 END * F_TAGPRICE(회사코드, 스타일)) AS \"수불창고금액\", ");
		strSQL.append("\n   0 AS \"완성장금액\", ");
		strSQL.append("\n   0 AS \"불량창고금액\", ");
		strSQL.append("\n   SUM(NVL(당월재고, 0) * F_TAGPRICE(회사코드, 스타일)) AS \"총재고금액\" ");
		strSQL.append("\n FROM {테이블1} ");
		strSQL.append("\n WHERE 회사코드 = '2' ");
		strSQL.append("\n   AND 수불년월 = :년월 ");
		strSQL.append("\n GROUP BY 센터코드 ");
		strSQL.append("\n UNION ALL ");
		strSQL.append("\n SELECT '5' AS \"구분\", ");
		strSQL.append("\n   0 AS \"행거창고\", ");
		strSQL.append("\n   0 AS \"평지이관창고\", ");
		strSQL.append("\n   0 AS \"파레트랙창고\", ");
		strSQL.append("\n   0 AS \"샘플창고\", ");
		strSQL.append("\n   0 AS \"행사창고\", ");
		strSQL.append("\n   0 AS \"반품창고\", ");
		strSQL.append("\n   0 AS \"수불창고\", ");
		strSQL.append("\n   SUM(CASE WHEN 완성처 IN ('JD', 'JM', 'JS', 'JK', 'JX') THEN NVL(당월재고,0) ELSE 0 END) AS \"완성장\", ");
		strSQL.append("\n   0 AS \"불량창고\", ");
		strSQL.append("\n   SUM(NVL(당월재고, 0)) AS \"총재고\", ");
		strSQL.append("\n   0 AS \"행거창고\", ");
		strSQL.append("\n   0 AS \"평지이관창고\", ");
		strSQL.append("\n   0 AS \"파레트랙창고\", ");
		strSQL.append("\n   0 AS \"샘플창고\", ");
		strSQL.append("\n   0 AS \"행사창고\", ");
		strSQL.append("\n   0 AS \"반품창고\", ");
		strSQL.append("\n   0 AS \"수불창고\", ");
		strSQL.append("\n   SUM(CASE WHEN 완성처 IN ('JD', 'JM', 'JS', 'JK', 'JX') THEN NVL(당월재고,0) ELSE 0 END * F_TAGPRICE(회사코드, 스타일)) AS \"완성장\", ");
		strSQL.append("\n   0 AS \"불량창고\", ");
		strSQL.append("\n   SUM(NVL(당월재고, 0) * F_TAGPRICE(회사코드, 스타일)) AS \"총재고\" ");
		strSQL.append("\n FROM {테이블2} ");
		strSQL.append("\n WHERE 회사코드 = '2' ");
		strSQL.append("\n   AND 수불년월 = :년월 ");
		strSQL.append("\n UNION ALL ");
		strSQL.append("\n SELECT 센터코드 AS \"구분\", ");
		strSQL.append("\n   0 AS \"행거창고\", ");
		strSQL.append("\n   0 AS \"평지이관창고\", ");
		strSQL.append("\n   0 AS \"파레트랙창고\", ");
		strSQL.append("\n   0 AS \"샘플창고\", ");
		strSQL.append("\n   0 AS \"행사창고\", ");
		strSQL.append("\n   0 AS \"반품창고\", ");
		strSQL.append("\n   0 AS \"수불창고\", ");
		strSQL.append("\n   0 AS \"완성장\", ");
		strSQL.append("\n   SUM(NVL(당월재고, 0)) AS \"불량창고\", ");
		strSQL.append("\n   SUM(NVL(당월재고, 0)) AS \"총재고\", ");
		strSQL.append("\n   0 AS \"행거창고\", ");
		strSQL.append("\n   0 AS \"평지이관창고\", ");
		strSQL.append("\n   0 AS \"파레트랙창고\", ");
		strSQL.append("\n   0 AS \"샘플창고\", ");
		strSQL.append("\n   0 AS \"행사창고\", ");
		strSQL.append("\n   0 AS \"반품창고\", ");
		strSQL.append("\n   0 AS \"수불창고\", ");
		strSQL.append("\n   0 AS \"완성장\", ");
		strSQL.append("\n   SUM(NVL(당월재고, 0) * F_TAGPRICE(회사코드, 스타일)) AS \"불량창고\", ");
		strSQL.append("\n   SUM(NVL(당월재고, 0) * F_TAGPRICE(회사코드, 스타일)) AS \"총재고\" ");
		strSQL.append("\n FROM  {테이블3} ");
		strSQL.append("\n WHERE 회사코드 = '2' ");
		strSQL.append("\n   AND 수불년월 = :년월 ");
		strSQL.append("\n GROUP BY 센터코드) ");
		strSQL.append("\nGROUP BY ROLLUP(구분) ");
		strSQL.append("\nHAVING SUM(NVL(총재고, 0)) != 0 ");
		strSQL.append("\nORDER BY 구분 ");

		def table1ForYear(pYear: String) = {
			if (pYear.toInt < 2013) {
				s"LOGIS_BACKUP.월별창고수불_${pYear}_세정21"
			} else {
				"LOGIS_SJ.월별창고수불"
			}
		}

		def table2ForYear(pYear: String) = {
			if (pYear.toInt < 2013) {
				s"LOGIS_BACKUP.완성처재고수불_${pYear}_세정21"
			} else {
				"LOGIS_SJ.완성처재고수불"
			}
		}

		def table3ForYear(pYear: String) = {
			if (pYear.toInt < 2013) {
				s"LOGIS_BACKUP.이등품스타일수불_${pYear}_세정21"
			} else {
				"LOGIS_SJ.이등품스타일수불"
			}
		}

		val columnList = List("구분명", "행거창고", "평지이관창고", "파레트랙창고", "샘플창고", "행사창고", "반품창고", "수불창고", "완성장", "불량창고", "총재고", "총재고금액")

		println("--------------- " + this.getClass.getName + " 시작" + " ---------------")
		println()

		val yearMonthList = (2004 to 2016).flatMap { year => (1 to 12).map { month => f"${year}$month%02d" } }.takeWhile(_ < "201606")

		val connOp = getDbConnection()

		val success = Future.successful(())

		val strQuery = strSQL.toString

		val query = strSQL.toString
		val resultFt = yearMonthList.map { yearMonth =>
			//Future {
			println(s"------------------------------${yearMonth} Start------------------------------")
			val query = strQuery
				.replaceAll("\\{테이블1\\}", table1ForYear(yearMonth.substring(0, 4)))
				.replaceAll("\\{테이블2\\}", table2ForYear(yearMonth.substring(0, 4)))
				.replaceAll("\\{테이블3\\}", table3ForYear(yearMonth.substring(0, 4)))
			val param = Map("년월" -> yearMonth)
			//DBUtil.logQuery(query, param)
			getDbConnection match {
				case Some(conn) =>
					val dataList = DBUtil.executeQuery(conn, query, param)
					println(s"------------------------------${yearMonth} Query Done------------------------------")
					if (dataList.size > 0) {
						val wb = new XSSFWorkbook();
						val sheet1 = wb.createSheet("월별물류센터총재고현황");
						val createHelper = wb.getCreationHelper();
						val headerRow = sheet1.createRow(0)
						headerRow.createCell(0).setCellValue(createHelper.createRichTextString("년월"))
						columnList.zipWithIndex.foreach {
							case (title, cellIdx) =>
								headerRow.createCell(cellIdx + 1).setCellValue(createHelper.createRichTextString(title))
						}

						dataList.zipWithIndex.foreach {
							case (data, rowIdx) =>
								val row = sheet1.createRow(rowIdx + 1)
								row.createCell(0).setCellValue(createHelper.createRichTextString(yearMonth))
								columnList.zipWithIndex.foreach {
									case (title, cellIdx) =>
										val cell = row.createCell(cellIdx + 1)
										val cellValue = data.getOrElse(title, "").toString
										if (dataList.size - 1 == rowIdx && title == "구분명") {
											cell.setCellValue("총재고")
											cell.setCellType(Cell.CELL_TYPE_STRING);
										} else if (title == "구분명" || cellValue == "") {
											cell.setCellValue(cellValue)
											cell.setCellType(Cell.CELL_TYPE_STRING);
										} else if (dataList.size - 1 == rowIdx && title == "총재고금액") {
											cell.setCellValue("")
										} else {
											val cellStyle = wb.createCellStyle();
											cellStyle.setDataFormat(wb.createDataFormat().getFormat("#,###"));
											cell.setCellStyle(cellStyle)
											cell.setCellValue(cellValue.toLong)
											cell.setCellType(Cell.CELL_TYPE_NUMERIC);
										}
								}
						}

						val lastRow = sheet1.createRow(dataList.size + 1)
						lastRow.createCell(0).setCellValue(createHelper.createRichTextString(yearMonth))
						columnList.map { title => if (title != "구분명" && title != "총재고금액" && title != "총재고") title + "금액" else title }.zipWithIndex.foreach {
							case (title, cellIdx) =>
								val cell = lastRow.createCell(cellIdx + 1)
								val cellValue = dataList.last.getOrElse(title, "").toString
								if (title == "구분명") {
									cell.setCellValue("총재고금액")
									cell.setCellType(Cell.CELL_TYPE_STRING)
								} else if (title == "총재고") {
									cell.setCellValue("")
								} else if (cellValue == "") {
									cell.setCellValue(cellValue)
									cell.setCellType(Cell.CELL_TYPE_STRING)
								} else {
									val cellStyle = wb.createCellStyle()
									cellStyle.setDataFormat(wb.createDataFormat().getFormat("#,###"))
									cell.setCellStyle(cellStyle)
									cell.setCellValue(cellValue.toLong)
									cell.setCellType(Cell.CELL_TYPE_NUMERIC)
								}
						}

						val fileOut = new FileOutputStream(s"월별총재고현황_세정21/월별물류센터총재고현황_${yearMonth}_세정21.xlsx");
						wb.write(fileOut);
						fileOut.close();
					}
					println(s"------------------------------${yearMonth} Done------------------------------")
			}
			//}.recover { case _ => () }
		}
		//Await.result(Future.sequence(resultFt), Duration.Inf)

		println()
		println("--------------- " + this.getClass.getName + " 완료" + " ---------------")
	}
}
