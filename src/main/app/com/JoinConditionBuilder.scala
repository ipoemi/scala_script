package script.com

object JoinConditionBuilder {
	def main(args: Array[String]) = {
		println("--------------- " + this.getClass.getName + " 시작" + " ---------------")
		println()

		val conditionBuilder = new StringBuilder()

		val joinConditionList = List("사업부", "접수상담실", "발생구분", "발생년도", "일련순번")

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