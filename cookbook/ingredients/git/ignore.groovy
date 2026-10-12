// A Git consumer decides how exported paths become native ignore patterns.
return { Object contribution ->
    if (contribution instanceof CharSequence) return contribution.toString()
    if (!(contribution instanceof Map) || !contribution.path)
        throw new IllegalArgumentException('Ignore contributions require a path')
    def path = contribution.path.toString()
    if (path.contains('\n') || path.contains('\r') || path.startsWith('!'))
        throw new IllegalArgumentException("Invalid ephemeral path: ${path}")
    def root = contribution.scope == 'root'
    (root ? '/' : '') + path + (contribution.kind == 'directory' ? '/' : '')
}
