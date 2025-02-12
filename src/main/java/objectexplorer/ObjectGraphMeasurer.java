package objectexplorer;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultiset;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multiset;
import java.util.EnumSet;

/**
 * A tool that can qualitatively measure the footprint ({@literal e.g.}, number of objects, references, primitives) of a graph structure.
 */
public class ObjectGraphMeasurer {
    /**
     * Measures the footprint of the specified object graph. The object graph is defined by a root object and whatever object can be reached through that,
     * excluding static fields, {@code Class} objects, and fields defined in {@code enum}s (all these are considered shared values, which should not contribute
     * to the cost of any single object graph).
     *
     * <p>Equivalent to {@code measure(rootObject, Predicates.alwaysTrue())}.
     *
     * @param rootObject the root object of the object graph
     * @return the footprint of the object graph
     */
    public static Footprint measure(Object rootObject) {
        return measure(rootObject, Predicates.alwaysTrue());
    }

    /**
     * Measures the footprint of the specified object graph. The object graph is defined by a root object and whatever object can be reached through that,
     * excluding static fields, {@code Class} objects, and fields defined in {@code enum}s (all these are considered shared values, which should not contribute
     * to the cost of any single object graph), and any object for which the user-provided predicate returns {@code false}.
     *
     * @param rootObject     the root object of the object graph
     * @param objectAcceptor a predicate that returns {@code true} for objects to be explored (and treated as part of the footprint), or {@code false} to forbid
     *                       the traversal to traverse the given object
     * @return the footprint of the object graph
     */
    public static Footprint measure(Object rootObject, Predicate<Object> objectAcceptor) {
        Preconditions.checkNotNull(objectAcceptor, "predicate");

        final Predicate<Chain> completePredicate = Predicates.and(ImmutableList.of(
            ObjectExplorer.notEnumFieldsOrClasses,
            Predicates.compose(objectAcceptor, ObjectExplorer.chainToObject),
            new ObjectExplorer.AtMostOncePredicate()
        ));

        return ObjectExplorer.exploreObject(rootObject, new ObjectGraphVisitor(completePredicate),
            EnumSet.of(ObjectExplorer.Feature.VISIT_PRIMITIVES, ObjectExplorer.Feature.VISIT_NULL)
        );
    }

    /**
     * The footprint of an object graph.
     */
    public static class Footprint {
        private final int objects;
        private final int references;
        private final ImmutableMultiset<Class<?>> primitives;

        private static final ImmutableSet<Class<?>> primitiveTypes = ImmutableSet.of(
            boolean.class, byte.class, char.class, short.class,
            int.class, float.class, long.class, double.class
        );

        /**
         * Constructs a Footprint, by specifying the number of objects, references, and primitives (represented as a {@link Multiset}).
         *
         * @param objects    the number of objects
         * @param references the number of references
         * @param primitives the number of primitives (represented by the respective primitive classes, e.g. {@code int.class} etc)
         */
        public Footprint(int objects, int references, Multiset<Class<?>> primitives) {
            Preconditions.checkArgument(objects >= 0, "Negative number of objects");
            Preconditions.checkArgument(references >= 0, "Negative number of references");
            Preconditions.checkArgument(
                primitiveTypes.containsAll(primitives.elementSet()),
                "Unexpected primitive type"
            );
            this.objects = objects;
            this.references = references;
            this.primitives = ImmutableMultiset.copyOf(primitives);
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                .add("Objects", this.objects)
                .add("References", this.references)
                .add("Primitives", this.primitives)
                .toString();
        }

        /**
         * Returns the number of objects of this footprint.
         */
        public int getObjects() {
            return this.objects;
        }

        /**
         * Returns the number of references of this footprint.
         */
        public int getReferences() {
            return this.references;
        }

        /**
         * Returns the number of primitives of this footprint (represented by the respective primitive classes, {@literal e.g.} {@code int.class} etc).
         */
        public ImmutableMultiset<Class<?>> getPrimitives() {
            return this.primitives;
        }
    }

    private static class ObjectGraphVisitor implements ObjectVisitor<Footprint> {
        private final Multiset<Class<?>> primitives = HashMultiset.create();
        private final Predicate<Chain> predicate;
        private int objects;
        // -1 to account for the root, which has no reference leading to it
        private int references = -1;

        ObjectGraphVisitor(Predicate<Chain> predicate) {
            this.predicate = predicate;
        }

        public Traversal visit(Chain chain) {
            if (chain.isPrimitive()) {
                this.primitives.add(chain.getValueType());
                return Traversal.SKIP;
            } else {
                this.references++;
            }
            if (this.predicate.apply(chain) && chain.getValue() != null) {
                this.objects++;
                return Traversal.EXPLORE;
            }
            return Traversal.SKIP;
        }

        public Footprint result() {
            return new Footprint(this.objects, this.references, ImmutableMultiset.copyOf(this.primitives));
        }
    }
}
