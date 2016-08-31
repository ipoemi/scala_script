package app.com

import java.sql.DriverManager
import scala.collection.mutable.ListBuffer
import java.sql.Connection
import scala.util.Try

object QueryHelper {

	private val dbInfoMap = Map(
		"sjlgs" -> Map("url" -> "jdbc:oracle:thin:@210.108.224.7:1521:ORA10g", "username" -> "sjlgs", "password" -> "sjlgs"))

	private def getDbConnection(dbName: String): Try[Connection] = {
		Try {
			val driver = "oracle.jdbc.driver.OracleDriver"
			val dbInfo = dbInfoMap(dbName)
			val url = dbInfo("url")
			val username = dbInfo("username")
			val password = dbInfo("password")
			Class.forName(driver)
			val dbCon = DriverManager.getConnection(url, username, password)
			dbCon.setAutoCommit(false)
			dbCon
		}
	}

	private val columnQueryBuilder = new StringBuilder;
	columnQueryBuilder.append("\nSELECT COLUMN_NAME, DATA_TYPE, DATA_LENGTH, DATA_PRECISION, DATA_SCALE ");
	columnQueryBuilder.append("\n  FROM USER_TAB_COLUMNS ");
	columnQueryBuilder.append("\n WHERE TABLE_NAME = '%s' ");
	columnQueryBuilder.append("\n ORDER BY COLUMN_ID ");

	private val pkQueryBuilder = new StringBuilder
	pkQueryBuilder.append("\nSELECT A.COLUMN_NAME ");
	pkQueryBuilder.append("\n  FROM ALL_CONS_COLUMNS A, USER_CONSTRAINTS B ");
	pkQueryBuilder.append("\n WHERE A.TABLE_NAME = B.TABLE_NAME ");
	pkQueryBuilder.append("\n   AND A.CONSTRAINT_NAME = B.CONSTRAINT_NAME ");
	pkQueryBuilder.append("\n   AND B.CONSTRAINT_TYPE = 'P' ");
	pkQueryBuilder.append("\n   AND A.TABLE_NAME = '%s' ");
	pkQueryBuilder.append("\n   AND A.OWNER = '%s' ");
	pkQueryBuilder.append("\n ORDER BY A.POSITION ");

	private val userNameBuilder = new StringBuilder
	userNameBuilder.append("\n SELECT USERNAME ");
	userNameBuilder.append("\n  FROM ALL_USERS ");
	userNameBuilder.append("\n WHERE USERNAME NOT LIKE '%OLD' ");
	userNameBuilder.append("\n   AND USERNAME NOT IN ('MARKET_A', 'MARKET', 'MARKET_J', 'MARKET_Q') ");
	userNameBuilder.append("\n ORDER BY USERNAME ");

	def getMergeStatement(dbName: String, username: String, tableName: String) = {
		val mergeStatementBuilder = new StringBuilder

		val connTry = getDbConnection(dbName)
		connTry.flatMap { connection =>
			val runTry = Try {
				val columnStatement = connection.createStatement()
				val columnResult = columnStatement.executeQuery(columnQueryBuilder.toString.format(tableName, username.toUpperCase))

				val pkStatement = connection.createStatement()
				val pkResult = pkStatement.executeQuery(pkQueryBuilder.toString.format(tableName, username.toUpperCase))

				val pkListBuffer: ListBuffer[String] = new ListBuffer
				while (pkResult.next()) {
					pkListBuffer += pkResult.getString(1)
				}

				val columnListBuffer: ListBuffer[String] = new ListBuffer
				while (columnResult.next()) {
					columnListBuffer += columnResult.getString(1)
				}

				val notPKColumnListBuffer = columnListBuffer -- pkListBuffer

				mergeStatementBuilder.append("\n MERGE INTO " + tableName + " A ");
				mergeStatementBuilder.append("\n USING (SELECT ");

				mergeStatementBuilder.append(":P_" + columnListBuffer.head + " ")
				mergeStatementBuilder.append(
					columnListBuffer reduce { (res, col) => res + " \n             , :P_" + col + " " + col })

				mergeStatementBuilder.append("\n          FROM DUAL) B ");
				mergeStatementBuilder.append("\n    ON ( ");

				mergeStatementBuilder.append("A." + pkListBuffer.head + " = B.")
				mergeStatementBuilder.append(
					pkListBuffer reduce { (res, col) => res + " \n         AND A." + col + " = B." + col })

				mergeStatementBuilder.append("\n       ) ");
				mergeStatementBuilder.append("\n  WHEN MATCHED THEN ");
				mergeStatementBuilder.append("\nUPDATE SET ");

				mergeStatementBuilder.append("A." + notPKColumnListBuffer.head + " = B.")
				mergeStatementBuilder.append(
					notPKColumnListBuffer reduce { (res, col) => res + " \n         , A." + col + " = B." + col })

				mergeStatementBuilder.append("\n  WHEN NOT MATCHED THEN ");
				mergeStatementBuilder.append("\nINSERT ( ");

				mergeStatementBuilder.append(
					columnListBuffer reduce { (res, col) => res + " \n       , " + col })

				mergeStatementBuilder.append(" ) ");
				mergeStatementBuilder.append("\nVALUES ( ");

				mergeStatementBuilder.append("B.")
				mergeStatementBuilder.append(
					columnListBuffer reduce { (res, col) => res + " \n       , B." + col })

				mergeStatementBuilder.append(" ) ");

			}
			connection.close()
			runTry
		}

		mergeStatementBuilder.toString

	}

	def getInsertStatement(dbName: String, username: String, tableName: String) = {
		val insertStatementBuilder = new StringBuilder

		val connTry = getDbConnection(dbName)
		connTry.flatMap { connection =>
			val runTry = Try {
				val columnStatement = connection.createStatement()
				val columnResult = columnStatement.executeQuery(columnQueryBuilder.toString.format(tableName, username.toUpperCase))

				val columnListBuffer: ListBuffer[String] = new ListBuffer
				while (columnResult.next()) {
					columnListBuffer += columnResult.getString(1)
				}

				insertStatementBuilder.append("\nINSERT ");
				insertStatementBuilder.append("\n  INTO " + tableName + " (");
				//insertStatementBuilder.append("\n         ") ;

				insertStatementBuilder.append(
					columnListBuffer reduce { (res, col) => res + " \n       , " + col })

				insertStatementBuilder.append(" ) ");
				insertStatementBuilder.append("\nVALUES ( ");

				insertStatementBuilder.append(":P_")
				insertStatementBuilder.append(
					columnListBuffer reduce { (res, col) => res + " \n       , :P_" + col })

				insertStatementBuilder.append(" ) ");

			}
			connection.close()
			runTry
		}

		insertStatementBuilder.toString

	}

	def getUpdateStatement(dbName: String, username: String, tableName: String) = {
		val updateStatementBuilder = new StringBuilder

		val connTry = getDbConnection(dbName)
		connTry.flatMap { connection =>
			val runTry = Try {
				val columnStatement = connection.createStatement()
				val columnResult = columnStatement.executeQuery(columnQueryBuilder.toString.format(tableName, username.toUpperCase))

				val pkStatement = connection.createStatement()
				val pkResult = pkStatement.executeQuery(pkQueryBuilder.toString.format(tableName, username.toUpperCase))

				val pkListBuffer: ListBuffer[String] = new ListBuffer
				while (pkResult.next()) {
					pkListBuffer += pkResult.getString(1)
				}

				val columnListBuffer: ListBuffer[String] = new ListBuffer
				while (columnResult.next()) {
					columnListBuffer += columnResult.getString(1)
				}

				updateStatementBuilder.append("\n UPDATE " + tableName + " A ");
				updateStatementBuilder.append("\n    SET ");

				updateStatementBuilder.append("A." + columnListBuffer.head + " = :P_")
				updateStatementBuilder.append(
					columnListBuffer reduce { (res, col) => res + " \n      , A." + col + " =  :P_" + col })

				updateStatementBuilder.append("\n  WHERE ");

				updateStatementBuilder.append("A." + pkListBuffer.head + " = :P_")
				updateStatementBuilder.append(
					pkListBuffer reduce { (res, col) => res + " \n    AND A." + col + " = :P_" + col })

			}
			connection.close()
			runTry
		}
		updateStatementBuilder.toString

	}

	def getDeleteStatement(dbName: String, username: String, tableName: String) = {
		val deleteStatementBuilder = new StringBuilder

		val connTry = getDbConnection(dbName)
		connTry.flatMap { connection =>
			val runTry = Try {
				val pkStatement = connection.createStatement()
				val pkResult = pkStatement.executeQuery(pkQueryBuilder.toString.format(tableName, username.toUpperCase))

				val pkListBuffer: ListBuffer[String] = new ListBuffer
				while (pkResult.next()) {
					pkListBuffer += pkResult.getString(1)
				}

				deleteStatementBuilder.append("\n DELETE ");
				deleteStatementBuilder.append("\n   FROM " + tableName + " A ");
				deleteStatementBuilder.append("\n  WHERE ");

				deleteStatementBuilder.append("A." + pkListBuffer.head + " = :P_")
				deleteStatementBuilder.append(
					pkListBuffer reduce { (res, col) => res + " \n    AND A." + col + " = :P_" + col })

			}
			connection.close()
			runTry
		}

		deleteStatementBuilder.toString

	}

	def getInsertTrigger(dbName: String, username: String, tableName: String) = {
		val insertTriggerStatementBuilder = new StringBuilder

		val connTry = getDbConnection(dbName)
		connTry.flatMap { connection =>
			val runTry = Try {
				val columnStatement = connection.createStatement()
				val columnResult = columnStatement.executeQuery(columnQueryBuilder.toString.format(tableName, username.toUpperCase))

				case class RowSchema(columnName: String, dataType: String, dataLength: Int, dataPrecision: Int, dataScale: Int)

				val columnListBuffer: ListBuffer[RowSchema] = new ListBuffer
				while (columnResult.next()) {
					columnListBuffer +=
						RowSchema(columnResult.getString(1),
							columnResult.getString(2),
							columnResult.getInt(3),
							columnResult.getInt(4),
							columnResult.getInt(5))
				}

				insertTriggerStatementBuilder.append("\nCREATE OR REPLACE TRIGGER %s_I_B_T".format(tableName));
				insertTriggerStatementBuilder.append("\nBEFORE INSERT ON %s".format(tableName));
				insertTriggerStatementBuilder.append("\nREFERENCING NEW AS NEW OLD AS OLD");
				insertTriggerStatementBuilder.append("\nFOR EACH ROW");
				insertTriggerStatementBuilder.append("\nDECLARE");
				insertTriggerStatementBuilder.append("\nBEGIN");
				insertTriggerStatementBuilder.append("\n  IF :NEW.IP IS NULL THEN");
				insertTriggerStatementBuilder.append("\n    BEGIN");
				insertTriggerStatementBuilder.append("\n      SELECT SYS_CONTEXT('USERENV','IP_ADDRESS') IP INTO :NEW.IP FROM DUAL;");
				insertTriggerStatementBuilder.append("\n  END;");
				insertTriggerStatementBuilder.append("\n  END IF;");
				insertTriggerStatementBuilder.append("\nEND;");

			}
			connection.close()
			runTry
		}

		insertTriggerStatementBuilder.toString

	}

	def getDeleteTrigger(dbName: String, username: String, tableName: String) = {
		val deleteTriggerStatementBuilder = new StringBuilder

		val connTry = getDbConnection(dbName)
		connTry.flatMap { connection =>
			val runTry = Try {
				val columnStatement = connection.createStatement()
				val columnResult = columnStatement.executeQuery(columnQueryBuilder.toString.format(tableName, username.toUpperCase))

				case class RowSchema(columnNo: Int, columnName: String, dataType: String, dataLength: Int, dataPrecision: Int, dataScale: Int)

				val columnListBuffer: ListBuffer[RowSchema] = new ListBuffer
				var i = 0
				while (columnResult.next()) {
					columnListBuffer +=
						RowSchema(i,
							columnResult.getString(1),
							columnResult.getString(2),
							columnResult.getInt(3),
							columnResult.getInt(4),
							columnResult.getInt(5))
					i += 1
				}

				deleteTriggerStatementBuilder.append("\nCREATE OR REPLACE TRIGGER %s_D_B_T".format(tableName));
				deleteTriggerStatementBuilder.append("\nBEFORE DELETE ON %s".format(tableName));
				deleteTriggerStatementBuilder.append("\nREFERENCING NEW AS NEW OLD AS OLD");
				deleteTriggerStatementBuilder.append("\nFOR EACH ROW");
				deleteTriggerStatementBuilder.append("\nDECLARE");
				deleteTriggerStatementBuilder.append("\n  W_IP   VARCHAR2(20) := '@';");
				deleteTriggerStatementBuilder.append("\n  W_HOST VARCHAR2(50) := '@';");
				deleteTriggerStatementBuilder.append("\nBEGIN");
				deleteTriggerStatementBuilder.append("\n  BEGIN");
				deleteTriggerStatementBuilder.append("\n    SELECT SYS_CONTEXT('USERENV','IP_ADDRESS') IP, SYS_CONTEXT('USERENV','HOST') HOST INTO W_IP, W_HOST FROM DUAL;");
				deleteTriggerStatementBuilder.append("\n  END;");
				deleteTriggerStatementBuilder.append("\n  BEGIN");
				deleteTriggerStatementBuilder.append("\n    INSERT INTO %s_H ( LOGDATE, LOGIP, HOST, TYPE,".format(tableName));

				columnListBuffer.foreach { rowSchema =>
					if (rowSchema.columnNo == 0) deleteTriggerStatementBuilder.append("\n                                  ")
					else if (rowSchema.columnNo % 5 == 0) deleteTriggerStatementBuilder.append(", \n                                  ")
					else deleteTriggerStatementBuilder.append(", ")
					deleteTriggerStatementBuilder.append(rowSchema.columnName)
				}
				deleteTriggerStatementBuilder.append(")")
				deleteTriggerStatementBuilder.append("\n                         VALUES ( SYSDATE, W_IP, W_HOST, 'D', ");

				columnListBuffer.foreach { rowSchema =>
					if (rowSchema.columnNo == 0) deleteTriggerStatementBuilder.append("\n                                  ")
					else if (rowSchema.columnNo % 5 == 0) deleteTriggerStatementBuilder.append(", \n                                  ")
					else deleteTriggerStatementBuilder.append(", ")
					deleteTriggerStatementBuilder.append(":OLD." + rowSchema.columnName)
				}
				deleteTriggerStatementBuilder.append(");")

				deleteTriggerStatementBuilder.append("\n    EXCEPTION WHEN OTHERS THEN NULL;");
				deleteTriggerStatementBuilder.append("\n  END;");
				deleteTriggerStatementBuilder.append("\nEND;");

			}
			connection.close()
			runTry
		}
		deleteTriggerStatementBuilder.toString

	}

	def getUpdateTrigger(dbName: String, username: String, tableName: String) = {
		val updateTriggerStatementBuilder = new StringBuilder

		val connTry = getDbConnection(dbName)
		connTry.flatMap { connection =>
			val runTry = Try {
				val columnStatement = connection.createStatement()
				val columnResult = columnStatement.executeQuery(columnQueryBuilder.toString.format(tableName, username.toUpperCase))

				case class RowSchema(columnNo: Int, columnName: String, dataType: String, dataLength: Int, dataPrecision: Int, dataScale: Int)

				val columnListBuffer: ListBuffer[RowSchema] = new ListBuffer
				var i = 0
				while (columnResult.next()) {
					columnListBuffer +=
						RowSchema(i,
							columnResult.getString(1),
							columnResult.getString(2),
							columnResult.getInt(3),
							columnResult.getInt(4),
							columnResult.getInt(5))
					i += 1
				}

				updateTriggerStatementBuilder.append("\nCREATE OR REPLACE TRIGGER %s_U_B_T".format(tableName));
				updateTriggerStatementBuilder.append("\nBEFORE UPDATE ON %s".format(tableName));
				updateTriggerStatementBuilder.append("\nREFERENCING NEW AS NEW OLD AS OLD");
				updateTriggerStatementBuilder.append("\nFOR EACH ROW");
				updateTriggerStatementBuilder.append("\nDECLARE");
				updateTriggerStatementBuilder.append("\n  W_IP   VARCHAR2(20) := '@';");
				updateTriggerStatementBuilder.append("\n  W_HOST VARCHAR2(50) := '@';");
				updateTriggerStatementBuilder.append("\nBEGIN");
				updateTriggerStatementBuilder.append("\n  W_IP := :NEW.IP;");
				updateTriggerStatementBuilder.append("\n  IF :NEW.IP IS NULL THEN");
				updateTriggerStatementBuilder.append("\n    BEGIN");
				updateTriggerStatementBuilder.append("\n      SELECT SYS_CONTEXT('USERENV','IP_ADDRESS') IP, SYS_CONTEXT('USERENV','HOST') HOST INTO W_IP, W_HOST FROM DUAL;");
				updateTriggerStatementBuilder.append("\n    END;");
				updateTriggerStatementBuilder.append("\n  END IF;");
				updateTriggerStatementBuilder.append("\n  BEGIN");
				updateTriggerStatementBuilder.append("\n    INSERT INTO %s_H ( LOGDATE, LOGIP, HOST, TYPE,".format(tableName));

				columnListBuffer.foreach { rowSchema =>
					if (rowSchema.columnNo == 0) updateTriggerStatementBuilder.append("\n                                  ")
					else if (rowSchema.columnNo % 5 == 0) updateTriggerStatementBuilder.append(", \n                                  ")
					else updateTriggerStatementBuilder.append(", ")
					updateTriggerStatementBuilder.append(rowSchema.columnName)
				}
				updateTriggerStatementBuilder.append(")")
				updateTriggerStatementBuilder.append("\n                         VALUES ( SYSDATE, W_IP, W_HOST, 'U', ");

				columnListBuffer.foreach { rowSchema =>
					if (rowSchema.columnNo == 0) updateTriggerStatementBuilder.append("\n                                  ")
					else if (rowSchema.columnNo % 5 == 0) updateTriggerStatementBuilder.append(", \n                                  ")
					else updateTriggerStatementBuilder.append(", ")
					updateTriggerStatementBuilder.append(":OLD." + rowSchema.columnName)
				}
				updateTriggerStatementBuilder.append(");")

				updateTriggerStatementBuilder.append("\n    EXCEPTION WHEN OTHERS THEN NULL;");
				updateTriggerStatementBuilder.append("\n  END;");
				updateTriggerStatementBuilder.append("\nEND;");

			}
			connection.close()
			runTry
		}

		updateTriggerStatementBuilder.toString

	}

	def getAllBusinessPartQuery(dbName: String, username: String, query: String) = {
		val allBusinessPartQueryBuilder = new StringBuilder

		val connTry = getDbConnection(dbName)
		connTry.flatMap { connection =>
			val runTry = Try {
				val userNameStatement = connection.createStatement()
				val userNameResult = userNameStatement.executeQuery(userNameBuilder.toString)

				case class RowSchema(no: Int, userName: String)

				val userNameListBuffer: ListBuffer[RowSchema] = new ListBuffer
				var i = 0
				while (userNameResult.next()) {
					userNameListBuffer += RowSchema(i, userNameResult.getString(1))
					i += 1
				}

				userNameListBuffer.foreach { rowSchema =>
					if (rowSchema.no == 0) allBusinessPartQueryBuilder.append(query.replace("{USER_NAME}", rowSchema.userName))
					else allBusinessPartQueryBuilder.append("\n UNION ALL" + query.replace("{USER_NAME}", rowSchema.userName))
				}

			}
			connection.close()
			runTry
		}

		allBusinessPartQueryBuilder.toString

	}

	def main(args: Array[String]) = {
		println("--------------- " + this.getClass.getName + " 시작" + " ---------------")
		println()
		
		println(getDeleteStatement("sjlgs", "sjlgs", "온라인센터입고처리내역"))

		println()
		println("--------------- " + this.getClass.getName + " 완료" + " ---------------")
	}

}