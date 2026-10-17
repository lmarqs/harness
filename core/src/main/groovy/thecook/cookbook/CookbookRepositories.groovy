package thecook.cookbook

/**
 * The repositories that cookbook artifacts resolve from, in the order they are tried: the first that holds an artifact
 * supplies it. `origin` names where the list came from in messages.
 */
final class CookbookRepositories {
  final List<CookbookRepository> repositories
  private final String origin

  CookbookRepositories(List<CookbookRepository> repositories, String origin) {
    this.repositories = repositories
    this.origin = origin
  }

  /** The artifact that `coordinates` name, read from the first repository holding it. */
  CookbookArtifact artifact(Coordinates coordinates) {
    for (CookbookRepository repository : repositories) {
      def artifact = repository.artifact(coordinates)
      if (artifact != null) {
        return artifact
      }
    }
    throw new IllegalArgumentException(
    "${coordinates.artifactAddress} not found in ${origin}; publish it there, or list its repository in cook.toml")
  }
}

/** A repository that cookbook artifacts resolve from. Its `toString` names it in messages. */
interface CookbookRepository {
  /** The artifact that `coordinates` name, or `null` when this repository does not hold it. */
  CookbookArtifact artifact(Coordinates coordinates)
}
