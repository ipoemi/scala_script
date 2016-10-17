import scala.collection.mutable.Stack

import org.junit.runner.RunWith
import org.scalatest._
import org.scalatest.junit.JUnitRunner
import com.util.Memoize

@RunWith(classOf[JUnitRunner])
class ExampleSpec extends FlatSpec with Matchers {
	
	def fuse[A, B](a: Option[A], b: Option[B]): Option[(A, B)] = {
		for (x <- a; y <- b) yield (x, y)
	}
	
	"fuse" should "" in {
		fuse(Some(1), Some(2)) should be(Some((1, 2)))
		fuse(None, Some(2)) should be(None)
		fuse(Some(1), None) should be(None)
		fuse(None, None) should be(None)
	}

	"A Stack" should "pop values in last-in-first-out order" in {
		val stack = new Stack[Int]
		stack.push(1)
		stack.push(2)
		stack.pop() should be(2)
		stack.pop() should be(1)
	}

	it should "throw NoSuchElementException if an empty stack is popped" in {
		val emptyStack = new Stack[Int]
		a[NoSuchElementException] should be thrownBy {
			emptyStack.pop()
		}
	}
}
