package thecook.filesystem

import java.nio.channels.FileChannel
import java.nio.channels.OverlappingFileLockException
import java.nio.file.StandardOpenOption

class RepositoryLock {
  static void exclusive(Repository repository, Closure operation) {
    def file = repository.metadata('lock'); file.parentFile.mkdirs()
    FileChannel.open(file.toPath(), StandardOpenOption.CREATE, StandardOpenOption.WRITE).withCloseable { channel ->
      def held
      try {
        held = channel.tryLock()
      }
      catch (OverlappingFileLockException error) {
        throw new IllegalStateException('Another Cook apply is running', error)
      }
      if (!held) throw new IllegalStateException('Another Cook apply is running')
      try {
        operation.call()
      } finally {
        held.release()
      }
    }
  }
}
