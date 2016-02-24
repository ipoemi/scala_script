package script.com

import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

import scala.collection.mutable.Queue

import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam

object JoinConditionBuilder extends App {
	println("--------------- " + this.getClass.getName + " 시작" + " ---------------")
	println()
	
	val conditionBuilder = new StringBuilder()
	
	val joinConditionList = List("회사코드", "센터코드", "창고구분", "스타일", "색상", "규격")
	
	val masterAlias = "A"

	val subAliasList = List("B")
	
	val isOutJoin = true
	
	val aliasTupleList = for (a1 <- subAliasList if a1 != masterAlias) yield (masterAlias, a1)
	
	aliasTupleList.foreach { tu =>
		joinConditionList.foreach { con =>
			conditionBuilder.append("\nAND " + tu._1 + "." + con + " = " + tu._2 + "." + con)
			if (isOutJoin) conditionBuilder.append(" (+)")
		}
	}
	
	println(conditionBuilder.toString)
	
	println()
	println("--------------- " + this.getClass.getName + " 완료" + " ---------------")

}