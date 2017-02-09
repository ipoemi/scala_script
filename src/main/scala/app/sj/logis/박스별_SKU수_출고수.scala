package app.sj.logis

import java.io.FileOutputStream
import java.sql.DriverManager

import com.util.db.DBUtil
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.xssf.usermodel.XSSFWorkbook

import scala.util.Try

object 박스별_SKU수_출고수 {

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
    strSQL.append("\nSELECT 센터코드, ")
    strSQL.append("\n       출고일자, ")
    strSQL.append("\n       매장코드, ")
    strSQL.append("\n       작업차수, ")
    strSQL.append("\n       박스번호, ")
    strSQL.append("\n       COUNT(DISTINCT 스타일||색상||규격) SKU수, ")
    strSQL.append("\n       SUM(NVL(솔리드수량, 0)) 출고수량 ")
    strSQL.append("\n  FROM DAS작업실적 ")
    strSQL.append("\n WHERE 출고일자 BETWEEN :년월||'01' AND :년월||'31' ")
    strSQL.append("\n GROUP BY 센터코드, 출고일자, 매장코드, 작업차수, 박스번호 ")
    strSQL.append("\n ORDER BY 센터코드, 출고일자, 매장코드, 작업차수, 박스번호 ")

    println("--------------- " + this.getClass.getName + " 시작" + " ---------------")
    println()

    val yearMonthList = (2016 to 2016).flatMap { year => (1 to 12).map { month => f"${year}$month%02d" } }

    val connOp = getDbConnection

    connOp match {
      case Some(conn) => yearMonthList.foreach { yearMonth =>
        println(s"------------------------------${yearMonth} Start------------------------------")
        val query = strSQL.toString
        val param = Map("년월" -> yearMonth)
        DBUtil.logQuery(query, param)
        val dataList = DBUtil.executeQueryToStream(conn, query, param)
        if (dataList.nonEmpty) {
          val wb = new XSSFWorkbook()
          val sheet1 = wb.createSheet("박스별내역")
          val createHelper = wb.getCreationHelper
          val headerRow = sheet1.createRow(0)
          //headerRow.createCell(0).setCellValue(createHelper.createRichTextString("년월"))
          dataList.head.keys.zipWithIndex.foreach {
            case (title, cellIdx) =>
              headerRow.createCell(cellIdx).setCellValue(createHelper.createRichTextString(title))
          }

          dataList.zipWithIndex.foreach {
            case (data, rowIdx) =>
              val row = sheet1.createRow(rowIdx + 1)
              //row.createCell(0).setCellValue(createHelper.createRichTextString(yearMonth))
              data.keys.zipWithIndex.foreach {
                case (title, cellIdx) =>
                  val cell = row.createCell(cellIdx)
                  val cellValue = data.getOrElse(title, "").toString
                  if (title == "출고수량" || title == "SKU수") {
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

          val fileOut = new FileOutputStream(s"박스별내역/박스별내역${yearMonth}_세정.xlsx")
          wb.write(fileOut)
          fileOut.close()
        }
      }
    }

    println()
    println("--------------- " + this.getClass.getName + " 완료" + " ---------------")
  }

}
