package com.util.db

import java.sql.{Connection, PreparedStatement, ResultSet}
import java.util.regex.Pattern

import scala.collection.immutable.ListMap
import scala.collection.mutable.ListBuffer
import scala.reflect._
import scala.util.{Failure, Success, Try}

object DBUtil {

  implicit class ExtendedResultSet(rs: ResultSet) {

    def toIterator: Iterator[Map[String, Any]] = {
      new Iterator[Map[String, Any]] {
        var hasNext = rs.next()

        private def toData: Map[String, Any] = ListMap((for (idx <- 1 to rs.getMetaData.getColumnCount) yield rs.getMetaData.getColumnName(idx) -> rs.getString(idx)): _*)

        def next(): Map[String, Any] = {
          if (!hasNext) Iterator.empty.next()
          else {
            val data = toData
            hasNext = rs.next()
            if (!hasNext) {
              try {
                rs.close()
                rs.getStatement.close()
              } catch {
                case _: Throwable =>
                  if (rs != null) rs.close()
                  if (rs.getStatement != null) rs.getStatement.close()
              }
            }
            data
          }
        }
      }
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

  protected def resultSetToVector(rs: ResultSet): Vector[ListMap[String, Any]] = {
    val metaData = rs.getMetaData
    val vectorBuffer = Vector.newBuilder[ListMap[String, Any]]

    var cnt = 0
    while (rs.next()) {
      vectorBuffer += ListMap((for (idx <- 1 to metaData.getColumnCount) yield metaData.getColumnName(idx) -> (if (rs.getString(idx) == null) "" else rs.getString(idx))): _*)
      cnt += 1
      if (cnt > LIMIT_OF_DATA_COUNT) {
        throw new Exception("LIMIT_OF_DATA_COUNT Exception")
      }
    }
    vectorBuffer.result
  }

  private def makePreparedStatement(conn: Connection, sql: String, paramMap: Map[String, Any] = Map()): Try[PreparedStatement] = {
    val newSql = new StringBuffer()
    val params = ListBuffer[String]()
    val p = Pattern.compile(":[a-zA-Z_\uAC00-\uD7A3][a-zA-Z_0-9\uAC00-\uD7A3]*");
    // Parameter RegExp
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
    val prepTry = Try {
      conn.prepareStatement(newSql.toString)
    }
    prepTry.flatMap { prep =>
      Try {
        params.zipWithIndex.foreach {
          case (param, idx) => prep.setString(idx + 1, param)
        }
        prep
      }
    }
  }

  def executeQuery[S <: Seq[_]](conn: Connection, sql: String, paramMap: Map[String, Any] = Map()): Seq[Map[String, Any]] = {
    val prepTry = makePreparedStatement(conn, sql, paramMap)
    val rsTry = prepTry.flatMap { prep =>
      Try(prep.executeQuery())
    }
    val resultTry = rsTry.flatMap { rs =>
      Try(resultSetToVector(rs))
    }
    rsTry.map(_.close())
    prepTry.map(_.close())
    resultTry match {
      case Success(result) => result
      case Failure(ex) => ex.printStackTrace(); throw ex
    }
  }

  def executeQueryToIterator(conn: Connection, sql: String, paramMap: Map[String, Any] = Map()): Iterator[Map[String, Any]] = {
    val prepTry = makePreparedStatement(conn, sql, paramMap)
    val rsTry = prepTry.flatMap { prep =>
      Try(prep.executeQuery())
    }
    rsTry match {
      case Success(rs) => rs.toIterator
      case Failure(ex) => ex.printStackTrace(); throw ex
    }
  }


  def executeUpdate(conn: Connection, sql: String, paramMap: Map[String, Any] = Map()): Int = {
    var prep: Option[PreparedStatement] = None
    try {
      val newSql = new StringBuffer()
      val params = ListBuffer[String]()
      val p = Pattern.compile(":[a-zA-Z_\uAC00-\uD7A3][a-zA-Z_0-9\uAC00-\uD7A3]*");
      // Parameter RegExp
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
    for (paramMap <- paramMapList) yield {
      executeUpdate(conn, sql, paramMap)
    }
  }

  def logQuery(sql: String, paramMap: Map[String, Any] = Map()): Unit = {
    val newSql = new StringBuffer()
    val params = ListBuffer[String]()
    val p = Pattern.compile(":[a-zA-Z_\uAC00-\uD7A3][a-zA-Z_0-9\uAC00-\uD7A3]*");
    // Parameter RegExp
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
