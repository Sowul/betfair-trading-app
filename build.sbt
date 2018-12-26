name := "betfair-trading-app"

version := "1.0"

scalaVersion := "2.11.8"

// https://mvnrepository.com/artifact/org.scala-lang/scala-library
libraryDependencies += "org.scala-lang" % "scala-library" % "2.11.8"

libraryDependencies += "com.github.nscala-time" %% "nscala-time" % "2.14.0"

assemblyMergeStrategy in assembly := {
       case PathList("META-INF", xs @ _*) => MergeStrategy.discard
       case x => MergeStrategy.first
   }
