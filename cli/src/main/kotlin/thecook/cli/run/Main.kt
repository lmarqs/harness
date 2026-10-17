package thecook.cli.run

import thecook.cli.terminal.CookCommand
import thecook.infra.CookToml
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.PrintWriter
import kotlin.system.exitProcess

fun main(args: Array<String>) {
  val stdin = System.console()?.let { BufferedReader(InputStreamReader(System.`in`)) }
  val cache =
    File(System.getenv("XDG_CACHE_HOME") ?: File(System.getProperty("user.home"), ".cache").path, "the-cook/git")
  val command =
    CookCommand(
      PrintWriter(System.out, true),
      PrintWriter(System.err, true),
      { CookToml.repositories(it, cache) },
      stdin,
    )
  exitProcess(command.execute(args))
}
