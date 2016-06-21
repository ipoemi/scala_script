package com.util

import scala.reflect.ClassTag
import scala.reflect.runtime.universe._
import scala.language.implicitConversions

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.fasterxml.jackson.module.scala.experimental.ScalaObjectMapper

package object implicits {
	private val mapper = new ObjectMapper()
	mapper.registerModules(DefaultScalaModule)
	mapper.configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true);

	implicit def toListAddedReadJson(listO: List.type) = {
		new {
			def readValue[T: TypeTag](str: String) = {
				mapper.readValue(str, classOf[List[T]])
			}

			def toJsonString = {
				mapper.writeValueAsString(listO)
			}
		}
	}

	implicit def toMapAddedReadJson(mapO: Map.type) = {
		new {
			def readValue[K: TypeTag, V: TypeTag](str: String) = {
				mapper.readValue(str, classOf[Map[K, V]])
			}

		}
	}

	implicit class ExtendedMap[K, V](map: Map[K, V]) {
		def toJsonString = {
			mapper.writeValueAsString(map)
		}
	}

	implicit class ExtendedList[T](list: List[T]) {
		def toJsonString = {
			mapper.writeValueAsString(list)
		}
	}

}
