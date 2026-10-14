@param(['path'], doc = 'Where the Terraform file goes') String path
@param(['from'], doc = 'File holding the Terraform text') File from
@param(['endpoint'], default = 'http://localhost:4566', doc = 'LocalStack endpoint') String endpoint

@ingredient(Terraform) def terraform
@ingredient(Aws) def aws
@ingredient(Mise) def mise

terraform.file(path, from.text)
mise.task('localstack:resource', terraform.planCommand(path, aws.localEnvironment(endpoint)))
