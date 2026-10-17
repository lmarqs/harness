package thecook.filesystem

import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermissions

class AtomicFiles {
  void link(File file, String target) {
    if (target == null) {
      Files.deleteIfExists(file.toPath()); return
    }
    file.parentFile.mkdirs()
    def temporary = file.parentFile.toPath().resolve('.cook-' + UUID.randomUUID())
    try {
      Files.createSymbolicLink(temporary, java.nio.file.Path.of(target))
      Files.move(temporary, file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    } finally {
      Files.deleteIfExists(temporary)
    }
  }
  void write(File file, String content, String mode) {
    if (content == null) {
      Files.deleteIfExists(file.toPath()); return
    }
    file.parentFile.mkdirs()
    def temporary = Files.createTempFile(file.parentFile.toPath(), '.cook-', '.tmp')
    try {
      Files.writeString(temporary, content)
      if (mode) {
        Files.setPosixFilePermissions(temporary, PosixFilePermissions.fromString(mode))
      }
      Files.move(temporary, file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    } finally {
      Files.deleteIfExists(temporary)
    }
  }
}
