package thecook.rendering

/** Selected applications remain visible even when they render no files. */
class RenderedPlan(
  val outputs: List<RenderedFile>,
  val applications: Map<String, List<RenderedFile>>,
)
