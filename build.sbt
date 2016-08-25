name := "image processing"

version := "1.0"

scalaVersion := "2.11.8"

resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"
resolvers += "Spray Repository" at "http://repo.spray.io"

unmanagedBase := baseDirectory.value / "lib"

//libraryDependencies += "com.oracle" % "ojdbc14" % "10.2.0.4.0"
libraryDependencies += "org.scalaz" %% "scalaz-core" % "7.2.3"
libraryDependencies += "org.scalaz" %% "scalaz-concurrent" % "7.2.3"
libraryDependencies += "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.7.3"
libraryDependencies += "org.apache.poi" % "poi" % "3.14"
libraryDependencies += "org.apache.poi" % "poi-ooxml" % "3.14"
libraryDependencies += "net.ruippeixotog" %% "scala-scraper" % "1.0.0"
libraryDependencies += "com.typesafe.akka" %% "akka-actor" % "2.4.9"
libraryDependencies += "com.typesafe.akka" %% "akka-http-core" % "2.4.9"

scalacOptions ++= Seq("-Xlint", "-feature", "-unchecked", "-deprecation")

scalaSource in Compile := baseDirectory.value / "src" / "main"
scalaSource in Test := baseDirectory.value / "src" / "test"

mainClass := Some("Main")
