val scalaMajorVersion = "2.11"
val scalaFullVersion = "2.11.8"

lazy val root = (project in file(".")).
  settings(
		name := "scala script",
		version := "1.0",
		scalaVersion := scalaFullVersion,
		resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/",
		resolvers += "Spray Repository" at "http://repo.spray.io",
		unmanagedBase := baseDirectory.value / "lib",
		//libraryDependencies += "com.oracle" % "ojdbc14" % "10.2.0.4.0",
		libraryDependencies += "org.typelevel" %% "cats" % "0.7.0",
		libraryDependencies += "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.7.3",
		libraryDependencies += "org.apache.poi" % "poi" % "3.14",
		libraryDependencies += "org.apache.poi" % "poi-ooxml" % "3.14",
		libraryDependencies += "net.ruippeixotog" %% "scala-scraper" % "1.0.0",
		libraryDependencies += "com.typesafe.akka" %% "akka-actor" % "2.4.9",
		libraryDependencies += "com.typesafe.akka" %% "akka-http-core" % "2.4.9",
		libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.0" % "test",
		libraryDependencies += "junit" % "junit" % "4.8.1" % "test",
		libraryDependencies += "org.scalafx" %% "scalafx" % "8.0.92-R10",
		scalacOptions ++= Seq("-Xlint", "-feature", "-unchecked", "-deprecation"),
		//scalaSource in Compile := baseDirectory.value / "src" / "main",
		//scalaSource in Test := baseDirectory.value / "src" / "test",
		fork := true,
		EclipseKeys.withSource := true,
		EclipseKeys.eclipseOutput in ThisBuild in Compile := Some(s"target/scala-${scalaMajorVersion}/classes"),
		EclipseKeys.eclipseOutput in ThisBuild in Test := Some(s"target/scala-${scalaMajorVersion}/test-classes")
  )
