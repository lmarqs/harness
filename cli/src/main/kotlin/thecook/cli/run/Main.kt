package thecook.cli.run

import thecook.cli.terminal.CookCommand
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import kotlin.system.exitProcess

fun main(args: Array<String>) {
  val stdin = System.console()?.let { BufferedReader(InputStreamReader(System.`in`)) }
  val command = CookCommand(PrintWriter(System.out, true), PrintWriter(System.err, true), stdin)
  exitProcess(command.execute(args))
}
