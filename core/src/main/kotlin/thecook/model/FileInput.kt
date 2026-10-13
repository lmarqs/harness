package thecook.model

class FileInput {
  private val sources = mutableListOf<Provider<FileSet>>()

  fun add(files: FileSet) {
    sources.add(Provider { files })
  }

  fun from(files: Provider<FileSet>) {
    sources.add(files)
  }

  fun resolve(): FileSet = FileSet(sources.flatMap { it.get().files })
}
