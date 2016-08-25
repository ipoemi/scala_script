package app.others

import scala.reflect.runtime.universe._
import scala.language.implicitConversions
import scala.util.Random

object DiceTest {

	val score = Map((0, 0) -> 0, (0, 1) -> 1, (0, 2) -> -1, (1, 0) -> -1, (1, 1) -> 0, (1, 2) -> 1, (2, 0) -> 1, (2, 1) -> -1, (2, 2) -> 0)

	val seed1 = System.currentTimeMillis()
	val seed2 = System.currentTimeMillis()

	val rand1 = new Random(seed1)
	val rand2 = new Random(seed2)

	def p1(): Int = rand1.nextInt(2)
	def p2(): Int = rand2.nextInt(3)

	val n = 100
	val n2 = 100

	def getResult(): (Double, Double, Double) = {
		val result =
			((0.0, 0.0, 0.0) /: (1 to n)) { (res, _) =>
				val p1Value = p1()
				val p2Value = p2()
				score((p1Value, p2Value)) match {
					case 0 => (res._1, res._2, res._3 + 1)
					case 1 => (res._1, res._2 + 1, res._3)
					case -1 => (res._1 + 1, res._2, res._3)
				}
			}
		(result._1 / n, result._2 / n, result._3 / n)
	}

	def main(args: Array[String]) = {
		println("--------------- " + this.getClass.getName + " 시작" + " ---------------")
		val result = ((0, 0) /: ((1 to n2).map(_ => getResult()))) { (re1, re2) =>
			if (re2._1 > re2._2) (re1._1 + 1, re1._2)
			else if (re2._1 < re2._2) (re1._1, re1._2 + 1)
			else re1
		}
		println(result)
		//println((0 /: (1 to 175)) { (n, sum) => n + sum })
		println("--------------- " + this.getClass.getName + " 완료" + " ---------------")
	}

}
