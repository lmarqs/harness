return { kitchen, book, Map options ->
  def operations = book.script('recipes/operations.groovy')
  def module = operations.required(options, 'module').toString()
  if (!(module ==~ /[a-zA-Z0-9_.\/-]+/)) throw new IllegalArgumentException('Invalid Go module path')
  def version = operations.required(options, 'version').toString()
  def station = kitchen.station(options.id ?: 'go-service')
  def mise = options.mise ?: book.ingredient('mise', kitchen, [adopt: options.adopt])
  def go = book.ingredient('go', kitchen, [version: version, mise: mise, adopt: options.adopt])
  operations.whole(station, options.modulePath ?: 'go.mod', options.moduleText ?: ('module ' + module + '\n\ngo ' + version + '\n'), options.adopt == true)
  mise { tasks += [[name: 'go:test', run: 'go test ./...']] }
  [mise, go].each { station.includes(it) }
  station
}
