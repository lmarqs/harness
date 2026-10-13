import thecook.composition.Connections

// Resolve named contributions through the shared connection contract.
return { Collection contributions, String category ->
  new Connections([contributions]).merge(category)
}
