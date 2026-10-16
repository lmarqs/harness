package thecook.cli.run

import thecook.application.CookService
import thecook.cli.filesystem.FileRepositoryInitializer
import thecook.cli.terminal.CookCommand
import thecook.groovy.GroovyBatchLoader
import thecook.groovy.NativePlanning
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import kotlin.system.exitProcess

fun main(args: Array<String>) {
  val service = CookService(GroovyBatchLoader(), NativePlanning(), FileRepositoryInitializer())
  val stdin = System.console()?.let { BufferedReader(InputStreamReader(System.`in`)) }
  val command = CookCommand(service, PrintWriter(System.out, true), PrintWriter(System.err, true), stdin)
  exitProcess(command.execute(args))
}
