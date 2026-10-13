package thecook.composition

class Deferred {
  private static final ThreadLocal<List<String>> resolving = ThreadLocal.withInitial { [] }
  final String identity
  final Closure supplier

  Deferred(String identity, Closure supplier) {
    this.identity = identity; this.supplier = supplier
  }
  Object getValue() {
    def stack = resolving.get()
    if (stack.contains(identity)) throw new IllegalArgumentException("Cyclic export: ${(stack + identity).join(' -> ')}")
    stack.add(identity)
    try {
      supplier.call()
    } finally {
      stack.remove(stack.size() - 1)
    }
  }
}
