package app

object TestApp {
	def main(args: Array[String]): Unit = {
		println((1 to 100000).par.filter(x => x.toString == x.toString.reverse))
	}
}