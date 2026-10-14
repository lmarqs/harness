@param(['workflow'], doc = 'Workflow file under .github/workflows') String workflow
@param(['job'], doc = 'The job holding the cache step') String jobName
@param(['step'], default = 'cache', doc = 'The cache step id') String step
@param(['key'], doc = 'The new cache key expression') String key

@ingredient(Workflow) def ci

ci.file = workflow
ci.job(jobName).stepWith(step, 'key', key)
