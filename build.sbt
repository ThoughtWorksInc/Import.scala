name := "import"

organization in ThisBuild := "com.thoughtworks.import"

crossScalaVersions in ThisBuild := Seq("2.11.11", "2.12.2")

scalacOptions in Test += "-Xplugin:" + (packageBin in Compile).value

libraryDependencies += "org.scala-lang" % "scala-compiler" % scalaVersion.value % Provided

libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.3" % Test
