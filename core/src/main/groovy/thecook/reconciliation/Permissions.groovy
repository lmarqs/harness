package thecook.reconciliation

import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.PosixFilePermissions

class Permissions {
  static String parse(String mode) {
    if (!(mode ==~ /0?[0-7]{3}/)) {
      throw new IllegalArgumentException("Invalid file mode: ${mode}")
    }
    def bits = Integer.parseInt(mode, 8)
    def result = EnumSet.noneOf(PosixFilePermission)
    def permissions = PosixFilePermission.values()
    for (int index = 0; index < 9; index++) {
      if (bits & (1 << (8 - index))) {
        result.add(permissions[index])
      }
    }
    PosixFilePermissions.toString(result)
  }
}
