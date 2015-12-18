name := "image processing"

version := "1.0"

scalaVersion := "2.10.5"

//resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"
//resolvers += "Spray Repository" at "http://repo.spray.io"

unmanagedBase := baseDirectory.value / "lib"

//libraryDependencies += "com.oracle" % "ojdbc14" % "10.2.0.4.0"

scalacOptions ++= Seq("-unchecked", "-deprecation")

scalaSource in Compile := baseDirectory.value / "src"

mainClass := Some("Main")
