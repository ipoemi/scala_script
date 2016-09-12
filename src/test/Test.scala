import scala.language.postfixOps
import cats.Semigroup
import cats.implicits._

object Test {
	def main(args: Array[String]): Unit = {
		println(Semigroup[Int].combine(1, 2))
	}
}