@param(['module'], doc = 'Module path') String module
@param(['version'], doc = 'Module version, such as v1.2.3') String version
@param(['dir'], default = '.', doc = 'The module directory') String dir

@ingredient(Go) def go
@ingredient(Mise) def mise

go.directory = dir
go.require(module, version)
mise.task('dependency:go', go.tidyCommand())
