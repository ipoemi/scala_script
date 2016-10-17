package com.util;

import scala.collection.JavaConversions._
import java.util.concurrent.ConcurrentHashMap
import scala.collection.concurrent.{ Map => ConcurrentMap };

class Memoize1[-T, +R](f: T => R) extends (T => R) {
	import scala.collection.mutable
	private[this] val vals: ConcurrentMap[T, R] =
		new java.util.concurrent.ConcurrentHashMap[T, R]()

	def apply(x: T): R = {
		vals.getOrElse(x, {
			val y = f(x)
			vals += (x -> y)
			y
		})
	}
}

class Memoize2[-T1, -T2, +R](f: (T1, T2) => R) extends ((T1, T2) => R) {
	import scala.collection.mutable
	private[this] val vals: ConcurrentMap[(T1, T2), R] =
		new java.util.concurrent.ConcurrentHashMap[(T1, T2), R]()

	def apply(x1: T1, x2: T2): R = {
		vals.getOrElse((x1, x2), {
			val y = f(x1, x2)
			vals += ((x1, x2) -> y)
			y
		})
	}
}

object Memoize {
	def apply[T, R](f: T => R) = new Memoize1(f)

	def apply[T1, T2, R](f: (T1, T2) => R) = new Memoize2(f)
}