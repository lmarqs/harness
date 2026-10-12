package thecook.groovy

import groovy.transform.CompileStatic
import thecook.composition.Component
import thecook.composition.Kitchen
import thecook.model.*
import thecook.reconciliation.ChangePlan
import thecook.reconciliation.Reconciler
import thecook.rendering.Output

/** The only bridge between typed cookbook declarations and the existing reconciler. */
@CompileStatic
class BatchAdapter {
    static ChangePlan plan(File repository, Batch batch) {
        Kitchen kitchen = new Kitchen(repository)
        mount(kitchen, batch)
        new Reconciler().plan(kitchen.repository, kitchen.render())
    }

    static Component mount(Kitchen kitchen, Batch batch) {
        // The batch groups contributors; it does not claim a contributor identity.
        Component root = new Component(kitchen, batch.id, 'recipe')
        for (Contributor contributor : batch.contributors) {
            Component component = kitchen.ingredient(contributor.id.value)
            for (OutputSpec spec : contributor.outputs()) {
                if (spec.owner != contributor.id) throw new IllegalArgumentException('Output owner differs from its contributor')
                add(component, spec)
            }
            root.includes(component)
        }
        root.mount()
        root
    }

    private static void add(Component component, OutputSpec spec) {
        if (spec instanceof SymbolicLinkOutput) {
            Output output = component.output(spec.path.value, [owns: true, adopt: spec.adopt], {})
            output.symlink({ spec.target })
            return
        }
        if (spec instanceof BlockOutput) {
            Output output = component.output(spec.path.value, [:], {})
            output.block(spec.region, { spec.content.get() })
            return
        }
        if (spec instanceof StructuredOutput) {
            Output output = component.output(spec.path.value, [format: spec.format.name().toLowerCase(Locale.ROOT)], {})
            for (KeyContribution field : spec.keys) {
                addKey(output, field)
            }
            return
        }
        if (spec instanceof FileOutput) {
            Output output = component.output(spec.path.value,
                [owns: true, adopt: spec.adopt, mode: spec.executable ? '0755' : null], {})
            output.text({ spec.content.get() })
            return
        }
        throw new IllegalArgumentException("Unsupported output: ${spec.class.name}")
    }

    private static void addKey(Output output, KeyContribution field) {
        output.key(field.path, [adopt: field.adopt, whenPresent: field.whenPresent], { field.value.get() })
    }
}
