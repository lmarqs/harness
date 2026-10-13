package thecook.model

/** Suppliers resolve during planning, so configuration remains live until then. */
fun interface Provider<T> : java.util.function.Supplier<T> {
  override fun get(): T
}
