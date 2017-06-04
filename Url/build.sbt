scalacOptions in Test += "-Xplugin:" + (packageBin in Compile).value

libraryDependencies += "org.scala-lang" % "scala-compiler" % scalaVersion.value % Provided

libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.3" % Test