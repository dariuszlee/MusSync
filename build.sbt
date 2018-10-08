name := "MusSync"

resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"

fork in run := true

libraryDependencies += "com.typesafe.akka" %% "akka-actor" % "2.5.16"
libraryDependencies += "com.typesafe.akka" %% "akka-stream" % "2.5.16"
libraryDependencies += "com.softwaremill.sttp" %% "core" % "1.3.1"
libraryDependencies += "com.typesafe.play" %% "play-json" % "2.6.10"
