package thecook.model

import thecook.composition.ComponentId

interface Contributor {
    val id: ComponentId
    fun outputs(): List<OutputSpec>
}

interface RecipeDefinition {
    fun compose(): Batch
}

class Batch(name: String) {
    val id = ComponentId.of(name)
    private val members = linkedMapOf<ComponentId, Contributor>()
    val contributors: List<Contributor> get() = members.values.toList()

    fun include(contributor: Contributor): Batch {
        require(!members.containsKey(contributor.id)) { "Duplicate component: ${contributor.id}" }
        members[contributor.id] = contributor
        return this
    }
}
