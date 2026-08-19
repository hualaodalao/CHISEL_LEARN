ThisBuild / version := "0.1.0"
ThisBuild / scalaVersion := "2.13.18"

lazy val root = (project in file("."))
  .settings(
    name := "ChiselLearn",
    Compile / unmanagedSourceDirectories += baseDirectory.value / "src" / "utils",
    Compile / unmanagedSourceDirectories += baseDirectory.value / "src" / "gingercat" / "riscv",
    Compile / unmanagedSourceDirectories += baseDirectory.value / "src" / "gingercat" / "compat",
    Compile / unmanagedSourceDirectories += baseDirectory.value / "src" / "gingercat" / "regfile",
    Compile / unmanagedSourceDirectories += baseDirectory.value / "src" / "gingercat" / "fetch",
    Compile / unmanagedSourceDirectories += baseDirectory.value / "src" / "hive" / "scala" / "main",
    Test / unmanagedSourceDirectories += baseDirectory.value / "src" / "hive" / "scala" / "test",
    Compile / unmanagedSourceDirectories += baseDirectory.value / "src" / "foragerbee" / "scala" / "main",
    Test / unmanagedSourceDirectories += baseDirectory.value / "src" / "foragerbee" / "scala" / "test",
    Compile / unmanagedSourceDirectories += baseDirectory.value / "src" / "llc" / "scala" / "main",
    Test / unmanagedSourceDirectories += baseDirectory.value / "src" / "llc" / "scala" / "test",
    Compile / unmanagedSources / excludeFilter := "MaskedLiteral.scala",
    libraryDependencies ++= Seq(
      "org.chipsalliance" %% "chisel" % "7.13.0",
      "com.lihaoyi" %% "sourcecode" % "0.4.2", // regif: SymbolName 隐式取名（替代 SpinalHDL 宏）
      "org.scalatest" %% "scalatest" % "3.2.19" % Test
    ),
    addCompilerPlugin("org.chipsalliance" % "chisel-plugin" % "7.13.0" cross CrossVersion.full)
  )
