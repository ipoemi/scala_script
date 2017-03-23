package app.sj.logis

import java.io.FileOutputStream
import java.sql.DriverManager

import com.util.db.DBUtil
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.xssf.usermodel.XSSFWorkbook

import scala.util.Try

object 매장별월별브랜드별출고실적 {

  def main(args: Array[String]): Unit = {
    def getDbConnection = {
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

    val strSQL = new StringBuffer()
    strSQL.append("\nSELECT A.매장코드, B.매장명, SUBSTR(A.출고일자, 1, 6) 출고월, A.브랜드, SUM(NVL(A.출고수량, 0)) 출고수량 ");
    strSQL.append("\n  FROM 출고실적_제품 A, 매장 B ");
    strSQL.append("\n WHERE A.회사코드 = B.회사코드 (+) ");
    strSQL.append("\n   AND A.매장코드 = B.매장코드 (+) ");
    strSQL.append("\n   AND A.회사코드 = '1' ");
    strSQL.append("\n   AND A.센터코드 IN ('1') ");
    strSQL.append("\n   AND A.창고구분 IN ('11','31','32') ");
    strSQL.append("\n   AND A.년도 IN ('S') ");
    strSQL.append("\n   AND A.시즌 IN ('F','C','W','Z') ");
    strSQL.append("\n   AND A.매장코드 LIKE 'M%' ");
    strSQL.append("\n   AND A.전표구분 = 'A' ");
    strSQL.append("\n   AND A.출고일자 LIKE :년월||'%' ");
    strSQL.append("\n   AND NVL(A.출고수량, 0) != 0 ");
    strSQL.append("\n GROUP BY A.매장코드, B.매장명, SUBSTR(A.출고일자, 1, 6), A.브랜드 ");
    strSQL.append("\n ORDER BY 출고월, 매장코드, 브랜드 ");

    println("--------------- " + this.getClass.getName + " 시작" + " ---------------")
    println()

    val yearMonthList = (2016 to 2016).flatMap { year => (7 to 12).map { month => f"${year}$month%02d" } }

    val connOp = getDbConnection

    connOp match {
      case Some(conn) => yearMonthList.foreach { yearMonth =>
        println(s"------------------------------${yearMonth} Start------------------------------")
        val query = strSQL.toString
        val param = Map("년월" -> yearMonth)
        DBUtil.logQuery(query, param)
        val queryResult = DBUtil.executeQueryToIterator(conn, query, param)
        if (queryResult.hasNext) {
          val wb = new XSSFWorkbook()
          val sheet1 = wb.createSheet("매장별월별브랜드별출고실적")
          val createHelper = wb.getCreationHelper

          def aux(data: Map[String, Any], rowIdx: Int): Unit = {
            val row = sheet1.createRow(rowIdx + 1)
            //row.createCell(0).setCellValue(createHelper.createRichTextString(yearMonth))
            data.keys.zipWithIndex.foreach {
              case (title, cellIdx) =>
                val cell = row.createCell(cellIdx)
                val cellValue = data.getOrElse(title, "").toString
                if (title == "출고수량") {
                  val cellStyle = wb.createCellStyle()
                  cellStyle.setDataFormat(wb.createDataFormat().getFormat("#,###"))
                  cell.setCellStyle(cellStyle)
                  cell.setCellValue(cellValue.toLong)
                  cell.setCellType(Cell.CELL_TYPE_NUMERIC)
                } else {
                  cell.setCellValue(cellValue)
                  cell.setCellType(Cell.CELL_TYPE_STRING)
                }
            }
          }

          val headerRow = sheet1.createRow(0)
          //headerRow.createCell(0).setCellValue(createHelper.createRichTextString("년월"))
          val head = queryResult.next()
          head.keys.zipWithIndex.foreach {
            case (title, cellIdx) =>
              headerRow.createCell(cellIdx).setCellValue(createHelper.createRichTextString(title))
          }

          var rowIdx = 0
          aux(head, rowIdx)
          rowIdx += 1

          queryResult.foreach { data =>
            aux(data, rowIdx)
            rowIdx += 1
          }

          val fileOut = new FileOutputStream(s"매장별월별브랜드별출고실적/매장별월별브랜드별출고실적_${yearMonth}_세정.xlsx")
          wb.write(fileOut)
          fileOut.close()
        }
      }
    }

    println()
    println("--------------- " + this.getClass.getName + " 완료" + " ---------------")
  }

}
