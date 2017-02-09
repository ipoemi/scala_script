package app.com

object JoinConditionBuilder {
	def main(args: Array[String]): Unit = {
		println("--------------- " + this.getClass.getName + " 시작" + " ---------------")
		println()

		val conditionBuilder = new StringBuilder()

		val joinConditionList = "회사코드, 출고일자, 전표구분, 작업차수, 일련순번".split(",").map(_.trim())

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

}