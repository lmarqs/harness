@param(['workflow'], doc = 'Workflow file under .github/workflows') String workflow
@param(['job'], doc = 'The job to change') String jobName
@param(['region'], doc = 'AWS region') String region
@param(['role'], doc = 'IAM role to assume') String role
@param(['placement'], default = 'start', doc = 'start, end, before:<step id> or after:<step id>') String placement

@ingredient(Workflow) def ci
@ingredient(Aws) def aws

ci.file = workflow
def job = ci.job(jobName)
job.step(aws.credentialsStep(region, role), placement)
job.permissions(aws.oidcPermissions())
