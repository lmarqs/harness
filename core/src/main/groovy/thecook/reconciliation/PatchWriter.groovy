package thecook.reconciliation

class PatchWriter {
    String write(ChangePlan plan) { plan.changes.collect { patch(it) }.join('') }
    private static String patch(Map change) {
        def old = change.before.content?.readLines() ?: []
        def next = change.content?.readLines() ?: []
        def header = "diff --git a/${change.path} b/${change.path}\n" +
                     "--- ${change.before.exists ? 'a/' + change.path : '/dev/null'}\n" +
                     "+++ ${change.content != null ? 'b/' + change.path : '/dev/null'}\n"
        if (old == next) return header + "# mode ${change.before.mode} -> ${change.mode}\n"
        header + "@@ -${old ? 1 : 0},${old.size()} +${next ? 1 : 0},${next.size()} @@\n" +
        old.collect { '-' + it + '\n' }.join('') + next.collect { '+' + it + '\n' }.join('')
    }
}
