name := "MusSync"

resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"

fork in run := true
connectInput in run := true

libraryDependencies += "com.typesafe.akka" %% "akka-actor" % "2.5.16"
libraryDependencies += "com.typesafe.akka" %% "akka-stream" % "2.5.16"
libraryDependencies += "com.typesafe.akka" %% "akka-http"   % "10.1.5" 
libraryDependencies += "com.typesafe.akka" %% "akka-persistence" % "2.5.16"
libraryDependencies += "com.typesafe.akka" %% "akka-slf4j" % "2.5.16"
libraryDependencies += "com.typesafe.akka" %% "akka-testkit" % "2.5.16" 

libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.2.3"

libraryDependencies += "com.typesafe.play" %% "play-json" % "2.6.10"
libraryDependencies += "org.iq80.leveldb"            % "leveldb"          % "0.7"
libraryDependencies += "org.fusesource.leveldbjni"   % "leveldbjni-all"   % "1.8"

libraryDependencies += "org.apache.logging.log4j" % "log4j-api" % "2.11.1"
libraryDependencies += "org.apache.logging.log4j" % "log4j-core" % "2.11.1"
libraryDependencies += "org.apache.logging.log4j" %% "log4j-api-scala" % "11.0"

// DB
libraryDependencies += "org.postgresql" % "postgresql" % "42.2.5"
