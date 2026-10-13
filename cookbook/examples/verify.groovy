import thecook.cookbook.Cookbook

// Each verification gets a disposable repository; no external tools are started.
return { String name, Closure verification ->
  def directory = new File(System.getProperty('thecook.cookbook', 'cookbook'))
  def book = new Cookbook(directory)
  def root = File.createTempDir('cook-' + name + '-', '')
  try {
    verification.call(book, root)
    println "Verified ${name}"
    true
  } finally {
    root.deleteDir()
  }
}
