package thecook.cookbook.ingredients.agentpolicy

import groovy.transform.CompileStatic
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor

/** Reads version 1 policy YAML; rejects unknown fields and executable YAML tags. */
@CompileStatic
final class PolicyFile {
    private PolicyFile() {}

    static Policy read(File file) {
        if (!file.isFile()) throw new IllegalArgumentException('Policy file not found: ' + file)
        LoaderOptions options = new LoaderOptions()
        options.allowDuplicateKeys = false
        Object loaded = file.withReader('UTF-8') { Reader reader -> new Yaml(new SafeConstructor(options)).load(reader) }
        Map<?, ?> data = object(loaded, 'policy')
        fields(data, ['version', 'instructions', 'commands'], 'policy')
        if (data.get('version') != 1) throw new IllegalArgumentException('Policy version must be 1')
        List<String> instructions = strings(data.containsKey('instructions') ? data.get('instructions') : [], 'instructions')
        Map<?, ?> commands = object(data.containsKey('commands') ? data.get('commands') : [:], 'commands')
        fields(commands, ['allow', 'ask', 'deny'], 'commands')
        List<Policy.Command> rules = []
        for (Policy.Decision decision : Policy.Decision.values()) {
            String key = decision.name().toLowerCase(Locale.ROOT)
            Object entries = commands.containsKey(key) ? commands.get(key) : []
            if (!(entries instanceof List)) throw new IllegalArgumentException('commands.' + key + ' must be a list')
            for (Object entry : (List<?>) entries)
                rules.add(new Policy.Command(decision, strings(entry, 'commands.' + key + ' prefix')))
        }
        new Policy(instructions, rules)
    }

    private static Map<?, ?> object(Object value, String field) {
        if (!(value instanceof Map)) throw new IllegalArgumentException(field + ' must be an object')
        (Map<?, ?>) value
    }

    private static List<String> strings(Object value, String field) {
        if (!(value instanceof List)) throw new IllegalArgumentException(field + ' must be a list of strings')
        List<String> result = []
        for (Object item : (List<?>) value) {
            if (!(item instanceof String)) throw new IllegalArgumentException(field + ' must contain only strings')
            result.add((String) item)
        }
        result
    }

    private static void fields(Map<?, ?> value, List<String> allowed, String field) {
        if (value.keySet().any { Object key -> !allowed.contains(key) })
            throw new IllegalArgumentException('Unknown field in ' + field + '; expected ' + allowed.join(', '))
    }
}
