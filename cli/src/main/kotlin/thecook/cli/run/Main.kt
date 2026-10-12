package thecook.cli.run

import thecook.application.CookService
import thecook.cli.terminal.CookCommand
import thecook.cli.filesystem.FileRepositoryInitializer
import thecook.groovy.GroovyBatchLoader
import thecook.groovy.NativePlanning
import java.io.PrintWriter
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val service = CookService(GroovyBatchLoader(), NativePlanning(), FileRepositoryInitializer())
    val command = CookCommand(service, PrintWriter(System.out, true), PrintWriter(System.err, true))
    exitProcess(command.execute(args))
}
