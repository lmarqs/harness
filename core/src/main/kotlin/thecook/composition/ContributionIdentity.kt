package thecook.composition

/** Persistent ownership address, independent of the implementing class or batch name. */
data class ContributionIdentity(
  val instance: ComponentId,
  val region: String,
) {
  init {
    require(region.isNotEmpty() && region.none { it.code < 32 || it.code == 127 }) {
      "Invalid contribution region"
    }
  }

  override fun toString(): String = "${instance.value}/$region"

  companion object {
    @JvmStatic fun of(
      instance: String,
      region: String,
    ) = ContributionIdentity(ComponentId.of(instance), region)

    @JvmStatic fun parse(address: String): ContributionIdentity {
      val separator = address.indexOf('/')
      require(separator > 0) { "Invalid contribution address: $address" }
      return of(address.substring(0, separator), address.substring(separator + 1))
    }
  }
}
