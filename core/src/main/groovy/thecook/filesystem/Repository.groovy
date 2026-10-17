package thecook.filesystem

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption
import java.security.MessageDigest

class Repository {
  final File directory
  Repository(File directory) {
    def path = directory.toPath()
        .toAbsolutePath()
        .normalize()
    def part = path.root
    path.each { segment ->
      part = part.resolve(segment)

      if (Files.isSymbolicLink(part)) {
        throw new IllegalArgumentException('Symlink repository root or ancestor')
      }
    }
    this.directory = directory.canonicalFile
  }
  File target(String relative, boolean allowLeafLink = false) {
    TargetPath.of(relative)
    resolve(relative, allowLeafLink)
  }
  private File resolve(String relative, boolean allowLeafLink = false) {
    def current = directory.toPath()

    if (Files.isSymbolicLink(current)) {
      throw new IllegalArgumentException('Symlink repository root')
    }

    def segments = relative.split('/')
    segments.eachWithIndex { segment, index ->
      if (Files.exists(current) && !Files.isDirectory(current)) {
        throw new IllegalArgumentException("Output parent is a regular file: ${relative}")
      }

      current = current.resolve(segment)

      if (Files.isSymbolicLink(current) && !(allowLeafLink && index == segments.length - 1)) {
        throw new IllegalArgumentException("Symlink target: ${relative}")
      }
    }
    current.toFile()
  }
  Map snapshot(File file, boolean allowLink = false) {
    if (Files.isSymbolicLink(file.toPath())) {
      if (!allowLink) {
        throw new IllegalArgumentException("Symlink target: ${file}")
      }

      String target = Files.readSymbolicLink(file.toPath()).toString()
      return [exists: true, hash: digest(target.getBytes('UTF-8')), content: target, mode: null, kind: 'symlink']
    }

    if (!file.exists()) {
      return [exists: false, hash: null, content: null, mode: null]
    }

    if (!Files.isRegularFile(file.toPath(), LinkOption.NOFOLLOW_LINKS)) {
      throw new IllegalArgumentException("Not a regular file: ${file}")
    }

    def bytes = file.bytes
    def decoder = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
    String text

    try {
      text = decoder.decode(ByteBuffer.wrap(bytes)).toString()
    }
    catch (java.nio.charset.CharacterCodingException error) {
      throw new IllegalArgumentException("Invalid UTF-8 file: ${file}", error)
    }

    [exists: true, hash: digest(bytes), content: text, mode: permissions(file)]
  }
  static String digest(byte[] bytes) {
    MessageDigest.getInstance('SHA-256')
        .digest(bytes)
        .encodeHex()
        .toString()
  }
  static String permissions(File file) {
    def view = Files.getFileAttributeView(file.toPath(), java.nio.file.attribute.PosixFileAttributeView)
    view ? java.nio.file.attribute.PosixFilePermissions.toString(view.readAttributes().permissions()) : null
  }
}
