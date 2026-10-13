package thecook.composition

data class ComponentId(
  val value: String,
) {
  init {
    require(value.matches(Regex("[a-zA-Z0-9][a-zA-Z0-9_.:-]*"))) { "Invalid component identity: $value" }
  }

  override fun toString(): String = value

  companion object {
    @JvmStatic fun of(value: String): ComponentId = ComponentId(value)
  }
}
