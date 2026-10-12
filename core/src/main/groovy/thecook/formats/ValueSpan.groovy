package thecook.formats

// Locate a TOML value after its key, including quoted strings and multiline arrays.
class ValueSpan {
    static List<Integer> afterEquals(String text, int start) {
        int equals = keyEquals(text, start)
        int lineEnd = text.indexOf('\n', start)
        if (equals < 0 || (lineEnd >= 0 && equals > lineEnd))
            throw new IllegalArgumentException('TOML table ownership requires named leaves')
        int first = equals + 1
        while (first < text.length() && text.charAt(first) in [' ' as char, '\t' as char]) first++
        int cursor = first; int last = first; int depth = 0
        String quote = null; boolean escaped = false
        while (cursor < text.length()) {
            def character = text.charAt(cursor).toString()
            if (quote) {
                if (!escaped && text.startsWith(quote, cursor)) { cursor += quote.length(); quote = null; last = cursor; continue }
                escaped = quote.startsWith('"') && character == '\\' && !escaped
                cursor++; last = cursor; continue
            }
            if (character in ['"', "'"]) {
                quote = text.startsWith(character * 3, cursor) ? character * 3 : character
                cursor += quote.length(); last = cursor; continue
            }
            if (depth == 0 && character in ['#', '\n', '\r']) break
            if (depth == 0 && character in [',', '}']) break
            if (character == '#') {
                def following = text.indexOf('\n', cursor)
                if (following < 0) throw new IllegalArgumentException('Malformed TOML comment inside value')
                cursor = following + 1
                continue
            }
            if (character in ['[', '{']) depth++
            if (character in [']', '}']) depth--
            cursor++
            if (!Character.isWhitespace(character.charAt(0))) last = cursor
        }
        if (quote || depth != 0) throw new IllegalArgumentException('Malformed TOML value span')
        [first, last]
    }
    private static int keyEquals(String text, int start) {
        String quote = null; boolean escaped = false
        for (int cursor = start; cursor < text.length(); cursor++) {
            def character = text.charAt(cursor).toString()
            if (quote) {
                if (character == quote && !escaped) quote = null
                escaped = character == '\\' && !escaped
                continue
            }
            if (character in ['"', "'"]) { quote = character; continue }
            if (character == '=') return cursor
            if (character in ['\n', '\r']) return -1
        }
        -1
    }
}
