package com.util.db

import java.sql.Connection
import java.sql.PreparedStatement

import java.sql.ResultSet
import java.util.regex.Pattern

import scala.collection.immutable.ListMap
import scala.collection.mutable.ListBuffer
import scala.reflect.runtime.universe._
import scala.util.Failure
import scala.util.Success
import scala.util.Try
import scala.reflect._

object DBUtil {

	implicit class ExtendedResultSet(rs: ResultSet) {

		def toMap: Map[String, Any] = {
			ListMap((for (idx <- 1 to rs.getMetaData.getColumnCount) yield rs.getMetaData.getColumnName(idx) -> rs.getString(idx)): _*)
		}

		def toStream: Stream[Map[String, Any]] = {
			new Iterator[Map[String, Any]] {
				def hasNext: Boolean = {
					try {
						if (rs.next()) true
						else {
							rs.close()
							rs.getStatement.close()
							false
						}
					} catch {
						case _: Throwable =>
							if (rs != null) rs.close()
							if (rs.getStatement != null) rs.getStatement.close()
							false
					}
				}
				def next(): Map[String, Any] = ListMap((for (idx <- 1 to rs.getMetaData.getColumnCount) yield rs.getMetaData.getColumnName(idx) -> rs.getString(idx)): _*)
			}.toStream
		}
	}

	private final val TAG = this.getClass.getName()
	private final val PARAM_SEPERATOR = '|'
	private final val PARAM_SEPERATOR_FOR_REGEX = '|'
	private final val LIMIT_OF_DATA_COUNT = 100000

	protected def checkIncludePattern(startIdx: Int, endIdx: Int, targetString: String, pattern: Pattern): Boolean = {
		val m2 = pattern.matcher(targetString)
		while (m2.find()) {
			if (startIdx > m2.start() && endIdx < m2.end()) {
				return true
			}
		}
		false
	}

	protected def resultSetToList(rs: ResultSet): List[ListMap[String, Any]] = {
		val metaData = rs.getMetaData
		val listBuffer = new ListBuffer[ListMap[String, Any]]()

		while (rs.next()) {
			listBuffer += ListMap((for (idx <- 1 to metaData.getColumnCount) yield metaData.getColumnName(idx) -> (if (rs.getString(idx) == null) "" else rs.getString(idx))): _*)
			if (listBuffer.size > LIMIT_OF_DATA_COUNT) {
				throw new Exception("LIMIT_OF_DATA_COUNT Exception")
			}
		}
		listBuffer.toList
	}

	def executeQueryToStream(conn: Connection, sql: String, paramMap: Map[String, Any] = Map()): Seq[Map[String, Any]] = {
		executeQuery[Stream[_]](conn, sql, paramMap)
	}

	def executeQuery[S <: Seq[_]](conn: Connection, sql: String, paramMap: Map[String, Any] = Map())(implicit tagS: ClassTag[S]): Seq[Map[String, Any]] = {
		val newSql = new StringBuffer()
		val params = ListBuffer[String]()
		val p = Pattern.compile(":[a-zA-Z_\uAC00-\uD7A3][a-zA-Z_0-9\uAC00-\uD7A3]*"); // Parameter RegExp
		val m = p.matcher(sql)
		var preIdx = 0
		while (m.find()) {
			if (!this.checkIncludePattern(m.start(), m.end(), sql, Pattern.compile("'.*?'"))
				&& !this.checkIncludePattern(m.start(), m.end(), sql, Pattern.compile("--[^+].*"))
				&& !this.checkIncludePattern(m.start(), m.end(), sql, Pattern.compile("/\\*[^+].*?\\*/", Pattern.DOTALL))) {
				val multiParams = paramMap.get(m.group().substring(1)) match {
					case Some(s) => s.toString().split(this.PARAM_SEPERATOR_FOR_REGEX).toList
					case None => List("")
				}
				val strQuestion = multiParams.map { _ => "?" }.reduce { (s1, s2) => s1 + "," + s2 }

				newSql.append(sql.substring(preIdx, m.start()) + strQuestion)
				preIdx = m.start() + m.group().length()

				multiParams.foreach { param => params += param }
			}
		}
		newSql.append(sql.substring(preIdx))
		val prepTry = Try { conn.prepareStatement(newSql.toString) }
		prepTry.flatMap { prep =>
			Try {
				params.zipWithIndex.foreach {
					case (param, idx) => prep.setString(idx + 1, param)
				}
			}
		}
		val rsTry = prepTry.flatMap { prep => Try { prep.executeQuery() } }
		if (classTag[S] == classTag[Stream[_]]) {
			rsTry match {
				case Success(rs) => rs.toStream
				case Failure(e) => e.printStackTrace(); throw e
			}
		} else {
			val resultTry = rsTry.flatMap { rs => Try { resultSetToList(rs) } }
			rsTry.map(_.close())
			prepTry.map(_.close())
			resultTry match {
				case Success(result) => result
				case Failure(e) => e.printStackTrace(); throw e
			}
		}
	}

	def executeUpdate(conn: Connection, sql: String, paramMap: Map[String, Any] = Map()): Int = {
		var prep: Option[PreparedStatement] = None
		try {
			val newSql = new StringBuffer()
			val params = ListBuffer[String]()
			val p = Pattern.compile(":[a-zA-Z_\uAC00-\uD7A3][a-zA-Z_0-9\uAC00-\uD7A3]*"); // Parameter RegExp
			val m = p.matcher(sql)
			var preIdx = 0
			while (m.find()) {
				if (!this.checkIncludePattern(m.start(), m.end(), sql, Pattern.compile("'.*?'"))
					&& !this.checkIncludePattern(m.start(), m.end(), sql, Pattern.compile("--[^+].*"))
					&& !this.checkIncludePattern(m.start(), m.end(), sql, Pattern.compile("/\\*[^+].*?\\*/", Pattern.DOTALL))) {
					val multiParams = paramMap.get(m.group().substring(1)) match {
						case Some(s) => s.toString().split(this.PARAM_SEPERATOR_FOR_REGEX).toList
						case None => List("")
					}
					val strQuestion = multiParams.map { _ => "?" }.reduce { (s1, s2) => s1 + "," + s2 }

					newSql.append(sql.substring(preIdx, m.start()) + strQuestion)
					preIdx = m.start() + m.group().length()

					multiParams.foreach { param => params += param }
				}
			}
			newSql.append(sql.substring(preIdx))
			prep = Some(conn.prepareStatement(newSql.toString))
			params.zipWithIndex.foreach {
				case (param, idx) =>
					prep.get.setString(idx + 1, param)
			}
			val result = prep.get.executeUpdate()
			result
		} catch {
			case e: Exception =>
				e.printStackTrace()
				throw e
		} finally {
			if (prep.isDefined) prep.get.close()
		}
	}

	def executeUpdate(conn: Connection, sql: String, paramMapList: List[Map[String, Any]]): List[Int] = {
		for (paramMap <- paramMapList) yield { executeUpdate(conn, sql, paramMap) }
	}

	def logQuery(sql: String, paramMap: Map[String, Any] = Map()): Unit = {
		val newSql = new StringBuffer()
		val params = ListBuffer[String]()
		val p = Pattern.compile(":[a-zA-Z_\uAC00-\uD7A3][a-zA-Z_0-9\uAC00-\uD7A3]*"); // Parameter RegExp
		val m = p.matcher(sql)
		var preIdx = 0
		while (m.find()) {
			if (!this.checkIncludePattern(m.start(), m.end(), sql, Pattern.compile("'.*?'"))
				&& !this.checkIncludePattern(m.start(), m.end(), sql, Pattern.compile("--[^+].*"))
				&& !this.checkIncludePattern(m.start(), m.end(), sql, Pattern.compile("/\\*[^+].*?\\*/", Pattern.DOTALL))) {
				val multiParams = paramMap.get(m.group().substring(1)) match {
					case Some(s) => s.toString().split(this.PARAM_SEPERATOR_FOR_REGEX).toList
					case None => List("")
				}
				val strQuestion = "'" + multiParams.reduce { (s1, s2) => "" + s1 + "" + "','" + s2 } + "'"

				newSql.append(sql.substring(preIdx, m.start()) + strQuestion)
				preIdx = m.start() + m.group().length()

				multiParams.foreach { param => params += param }
			}
		}
		println("------------------------------SQL------------------------------")
		println(newSql.append(sql.substring(preIdx)))
		println("----------------------------//SQL------------------------------")
		println()
	}

	def logQuery(sql: String, paramMapList: List[Map[String, Any]]): Unit = {
		paramMapList.foreach { paramMap => logQuery(sql, paramMap) }
	}
}
