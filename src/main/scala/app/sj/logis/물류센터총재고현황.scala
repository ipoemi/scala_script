package app.sj.logis

import java.io.FileOutputStream
import java.sql.DriverManager

import scala.util.Try

import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.xssf.usermodel.XSSFWorkbook

import com.util.db.DBUtil

object 물류센터총재고현황 {

	def main(args: Array[String]): Unit = {
		def getDbConnection() = {
			Try {
				val driver = "oracle.jdbc.driver.OracleDriver"
				val url = "jdbc:oracle:thin:@210.108.224.7:1521:ORA10g"
				val username = "sjlgs"
				val password = "sjlgs"
				Class.forName(driver)
				val dbCon = Some(DriverManager.getConnection(url, username, password))
				dbCon.get.setAutoCommit(false)
				dbCon
			}.get
		}

		def tableForYear(pYear: String) = {
			if (pYear.toInt < 2013) {
				s"LOGIS_BACKUP.월별창고수불_${pYear}_세정"
			} else {
				"SJLGS.월별창고수불"
			}
		}

		val strSQL = new StringBuffer()
		strSQL.append("\nselect decode(센터코드,'1','서창1센터','3','서창3센터','7','신유통센터','D','불량반품센터','9','로스센터') 센터코드, sum(자동) 자동, sum(행거1) 행거1, sum(행거2) 행거2, sum(평지1) 평지1, ")
		strSQL.append("\n       sum(평지2) 평지2, sum(반품) 반품, sum(완성) 완성, sum(불량) 불량, sum(총재고) 총재고, sum(재고금액) 재고금액 ")
		strSQL.append("\nfrom( ")
		strSQL.append("\nselect 센터코드 , ")
		strSQL.append("\n       decode(sum(decode(창고구분,'11',nvl(당월재고,0),null)),0,null,sum(decode(창고구분,'11',nvl(당월재고,0),null))) 자동, ")
		strSQL.append("\n       decode(sum(decode(창고구분,'21',nvl(당월재고,0),null)),0,null,sum(decode(창고구분,'21',nvl(당월재고,0),null))) 행거1, ")
		strSQL.append("\n       decode(sum(decode(창고구분,'31',nvl(당월재고,0),null)),0,null,sum(decode(창고구분,'31',nvl(당월재고,0),null))) 평지1, ")
		strSQL.append("\n       decode(sum(decode(창고구분,'22',nvl(당월재고,0),null)),0,null,sum(decode(창고구분,'22',nvl(당월재고,0),null))) 행거2, ")
		strSQL.append("\n       decode(sum(decode(창고구분,'32',nvl(당월재고,0),null)),0,null,sum(decode(창고구분,'32',nvl(당월재고,0),null))) 평지2, ")
		strSQL.append("\n       decode(sum(decode(창고구분,'54',nvl(당월재고,0),null)),0,null,sum(decode(창고구분,'54',nvl(당월재고,0),null))) 반품, ")
		strSQL.append("\n       decode((sum(decode(창고구분,'57',nvl(당월재고,0),0)) + sum(decode(창고구분,'61',nvl(당월재고,0),0)) + sum(decode(창고구분,'62',nvl(당월재고,0),0))),0,null, ")
		strSQL.append("\n              (sum(decode(창고구분,'57',nvl(당월재고,0),0)) + sum(decode(창고구분,'61',nvl(당월재고,0),0)) + sum(decode(창고구분,'62',nvl(당월재고,0),0)))) 완성, ")
		strSQL.append("\n       decode(sum(decode(센터코드,'D',nvl(당월재고,0),null)),0,null,sum(decode(센터코드,'D',nvl(당월재고,0),null))) 불량, ")
		strSQL.append("\n       decode(sum(nvl(당월재고,0)),0,null,sum(nvl(당월재고,0))) 총재고, ")
		strSQL.append("\n       decode(sum(nvl(당월재고,0) * f_tagprice(회사코드, 스타일)),0,null,sum(nvl(당월재고,0) * f_tagprice(회사코드, 스타일))) 재고금액 ")
		strSQL.append("\nfrom {테이블} ")
		strSQL.append("\nwhere 회사코드 = '1' ")
		strSQL.append("\nand 수불년월 = :년월 ")
		strSQL.append("\nand 센터코드 in ('1','3','7','D','9') ")
		strSQL.append("\ngroup by 센터코드 ")
		strSQL.append("\n) ")
		strSQL.append("\ngroup by rollup(센터코드) ")

		println("--------------- " + this.getClass.getName + " 시작" + " ---------------")
		println()

		val yearMonthList = (2004 to 2016).flatMap { year => (1 to 12).map { month => f"${year}$month%02d" } }.takeWhile(_ < "201606")

		val connOp = getDbConnection()

		connOp match {
			case Some(conn) => yearMonthList.foreach { yearMonth =>
				println(s"------------------------------${yearMonth} Start------------------------------")
				val query = strSQL.toString.replaceAll("\\{테이블\\}", tableForYear(yearMonth.substring(0, 4)))
				val param = Map("년월" -> yearMonth)
				val dataList = DBUtil.executeQuery(conn, query, param)
				if (dataList.nonEmpty) {
					/*
				val header = "년월,센터코드,자동,행거1,행거2,평지1,평지2,반품,완성,불량,총재고,재고금액\n" 
				val file = new PrintWriter(new File(s"월별물류센터총재고현황_${yearMonth}_세정.csv"), "cp949")
				file.write(header)
				dataList.foreach { row =>
					val rowData = s"${yearMonth},${row("센터코드")},${row("자동")},${row("행거1")},${row("행거2")},${row("평지1")},${row("평지2")},${row("반품")},${row("완성")},${row("불량")},${row("총재고")},${row("재고금액")}\n"
					file.write(rowData)
				}
				file.close()
				*/
					val wb = new XSSFWorkbook()
					val sheet1 = wb.createSheet("월별물류센터총재고현황")
					val createHelper = wb.getCreationHelper()
					val headerRow = sheet1.createRow(0)
					headerRow.createCell(0).setCellValue(createHelper.createRichTextString("년월"))
					dataList.head.keys.zipWithIndex.foreach {
						case (title, cellIdx) =>
							headerRow.createCell(cellIdx + 1).setCellValue(createHelper.createRichTextString(title))
					}

					dataList.zipWithIndex.foreach {
						case (data, rowIdx) =>
							val row = sheet1.createRow(rowIdx + 1)
							row.createCell(0).setCellValue(createHelper.createRichTextString(yearMonth))
							data.keys.zipWithIndex.foreach {
								case (title, cellIdx) =>
									val cell = row.createCell(cellIdx + 1)
									val cellValue = data.getOrElse(title, "").toString
									if (title == "센터코드" || cellValue == "") {
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
					}

					val fileOut = new FileOutputStream(s"월별총재고현황_세정/월별물류센터총재고현황_${yearMonth}_세정.xlsx")
					wb.write(fileOut)
					fileOut.close()
				}
			}
		}

		println()
		println("--------------- " + this.getClass.getName + " 완료" + " ---------------")
	}

}
