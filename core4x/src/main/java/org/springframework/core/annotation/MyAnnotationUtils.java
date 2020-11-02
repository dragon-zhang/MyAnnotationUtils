package org.springframework.core.annotation;

import org.springframework.util.Assert;
import org.springframework.util.ConcurrentReferenceHashMap;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

public abstract class MyAnnotationUtils extends AnnotationUtils {

    private static final Map<Method, AliasDescriptor> aliasDescriptorCache =
            new ConcurrentReferenceHashMap<Method, AliasDescriptor>(256);
    /**
     * Get the name of the overridden attribute configured via
     * {@link MyAliasFor @AliasFor} for the supplied annotation {@code attribute}.
     *
     * @param attribute          the attribute from which to retrieve the override
     *                           (never {@code null})
     * @param metaAnnotationType the type of meta-annotation in which the
     *                           overridden attribute is allowed to be declared
     * @return the name of the overridden attribute, or {@code null} if not
     * found or not applicable for the specified meta-annotation type
     * @throws IllegalArgumentException         if the supplied attribute method is
     *                                          {@code null} or not from an annotation, or if the supplied meta-annotation
     *                                          type is {@code null} or {@link Annotation}
     * @throws AnnotationConfigurationException if invalid configuration of
     *                                          {@code @AliasFor} is detected
     * @since 4.2
     */
    static String[] getAttributeOverrideName(Class<? extends Annotation> metaAnnotationType, Method attribute) {
        Assert.notNull(attribute, "attribute must not be null");
        Assert.notNull(metaAnnotationType, "metaAnnotationType must not be null");
        Assert.isTrue(Annotation.class != metaAnnotationType,
                "metaAnnotationType must not be [java.lang.annotation.Annotation]");

        AliasDescriptor descriptor = AliasDescriptor.from(attribute);
        return (descriptor != null ? descriptor.getAttributeOverrideName(metaAnnotationType) : null);
    }

    /**
     * {@code AliasDescriptor} encapsulates the declaration of {@code @AliasFor}
     * on a given annotation attribute and includes support for validating
     * the configuration of aliases (both explicit and implicit).
     *
     * @see #from
     * @see #getAttributeAliasNames
     * @see #getAttributeOverrideName
     * @since 4.2.1
     */
    private static class AliasDescriptor {

        private final Method sourceAttribute;

        private final Class<? extends Annotation> sourceAnnotationType;

        private final String sourceAttributeName;

        private final Alias[] aliases;

        private static class Alias {

            private final Method aliasedAttribute;

            private final Class<? extends Annotation> aliasedAnnotationType;

            private final String aliasedAttributeName;

            private final boolean isAliasPair;

            private Alias(Method aliasedAttribute, Class<? extends Annotation> aliasedAnnotationType, String aliasedAttributeName, boolean isAliasPair) {
                this.aliasedAttribute = aliasedAttribute;
                this.aliasedAnnotationType = aliasedAnnotationType;
                this.aliasedAttributeName = aliasedAttributeName;
                this.isAliasPair = isAliasPair;
            }
        }

        /**
         * Create an {@code AliasDescriptor} <em>from</em> the declaration
         * of {@code @AliasFor} on the supplied annotation attribute and
         * validate the configuration of {@code @AliasFor}.
         *
         * @param attribute the annotation attribute that is annotated with
         *                  {@code @AliasFor}
         * @return an alias descriptor, or {@code null} if the attribute
         * is not annotated with {@code @AliasFor}
         * @see #validateAgainst
         */
        public static AliasDescriptor from(Method attribute) {
            AliasDescriptor descriptor = aliasDescriptorCache.get(attribute);
            if (descriptor != null) {
                return descriptor;
            }

            MyAliasFor[] aliasFors = getAliasFors(attribute);
            if (aliasFors == null) {
                return null;
            }

            descriptor = new AliasDescriptor(attribute, aliasFors);
            descriptor.validate();
            aliasDescriptorCache.put(attribute, descriptor);
            return descriptor;
        }

        /**
         * First try to obtain {@link AliasFors},if it cannot be obtained,
         * try to obtain {@link MyAliasFor} again, if it can be obtained,
         * return an array with only a single element, or return null
         * if it still cannot be obtained.
         *
         * @since 4.2.28
         */
        private static MyAliasFor[] getAliasFors(AnnotatedElement element) {
            AliasFors aliasFors = element.getAnnotation(AliasFors.class);
            if (aliasFors == null) {
                MyAliasFor aliasFor = element.getAnnotation(MyAliasFor.class);
                if (aliasFor == null) {
                    return null;
                }
                return new MyAliasFor[]{aliasFor};
            }
            return aliasFors.value();
        }

        @SuppressWarnings("unchecked")
        private AliasDescriptor(Method sourceAttribute, MyAliasFor[] aliasFors) {
            Class<?> declaringClass = sourceAttribute.getDeclaringClass();
            Assert.isTrue(declaringClass.isAnnotation(), "sourceAttribute must be from an annotation");

            this.sourceAttribute = sourceAttribute;
            this.sourceAnnotationType = (Class<? extends Annotation>) declaringClass;
            this.sourceAttributeName = sourceAttribute.getName();

            List<Alias> aliases = new ArrayList<Alias>(aliasFors.length);
            for (MyAliasFor aliasFor : aliasFors) {
                Class<? extends Annotation> aliasedAnnotationType = (Annotation.class == aliasFor.annotation() ?
                        this.sourceAnnotationType : aliasFor.annotation());
                String aliasedAttributeName = getAliasedAttributeName(aliasFor, sourceAttribute);
                if (aliasedAnnotationType == this.sourceAnnotationType &&
                        aliasedAttributeName.equals(this.sourceAttributeName)) {
                    String msg = String.format("@AliasFor declaration on attribute '%s' in annotation [%s] points to " +
                                    "itself. Specify 'annotation' to point to a same-named attribute on a meta-annotation.",
                            sourceAttribute.getName(), declaringClass.getName());
                    throw new AnnotationConfigurationException(msg);
                }
                Method aliasedAttribute;
                try {
                    aliasedAttribute = aliasedAnnotationType.getDeclaredMethod(aliasedAttributeName);
                } catch (NoSuchMethodException ex) {
                    String msg = String.format(
                            "Attribute '%s' in annotation [%s] is declared as an @AliasFor nonexistent attribute '%s' in annotation [%s].",
                            this.sourceAttributeName, this.sourceAnnotationType.getName(), aliasedAttributeName,
                            aliasedAnnotationType.getName());
                    throw new AnnotationConfigurationException(msg, ex);
                }

                boolean isAliasPair = (this.sourceAnnotationType == aliasedAnnotationType);
                aliases.add(new Alias(aliasedAttribute, aliasedAnnotationType, aliasedAttributeName, isAliasPair));
            }
            this.aliases = aliases.toArray(new Alias[0]);
        }

        private void validate() {
            for (Alias alias : this.aliases) {
                // Target annotation is not meta-present?
                if (!alias.isAliasPair && !isAnnotationMetaPresent(this.sourceAnnotationType, alias.aliasedAnnotationType)) {
                    String msg = String.format("@AliasFor declaration on attribute '%s' in annotation [%s] declares " +
                                    "an alias for attribute '%s' in meta-annotation [%s] which is not meta-present.",
                            this.sourceAttributeName, this.sourceAnnotationType.getName(), alias.aliasedAttributeName,
                            alias.aliasedAnnotationType.getName());
                    throw new AnnotationConfigurationException(msg);
                }

                if (alias.isAliasPair) {
                    MyAliasFor mirrorAliasFor = alias.aliasedAttribute.getAnnotation(MyAliasFor.class);
                    if (mirrorAliasFor == null) {
                        String msg = String.format("Attribute '%s' in annotation [%s] must be declared as an @AliasFor [%s].",
                                alias.aliasedAttributeName, this.sourceAnnotationType.getName(), this.sourceAttributeName);
                        throw new AnnotationConfigurationException(msg);
                    }

                    String mirrorAliasedAttributeName = getAliasedAttributeName(mirrorAliasFor, alias.aliasedAttribute);
                    if (!this.sourceAttributeName.equals(mirrorAliasedAttributeName)) {
                        String msg = String.format("Attribute '%s' in annotation [%s] must be declared as an @AliasFor [%s], not [%s].",
                                alias.aliasedAttributeName, this.sourceAnnotationType.getName(), this.sourceAttributeName,
                                mirrorAliasedAttributeName);
                        throw new AnnotationConfigurationException(msg);
                    }
                }

                Class<?> returnType = this.sourceAttribute.getReturnType();
                Class<?> aliasedReturnType = alias.aliasedAttribute.getReturnType();
                if (returnType != aliasedReturnType &&
                        (!aliasedReturnType.isArray() || returnType != aliasedReturnType.getComponentType())) {
                    String msg = String.format("Misconfigured aliases: attribute '%s' in annotation [%s] " +
                                    "and attribute '%s' in annotation [%s] must declare the same return type.",
                            this.sourceAttributeName, this.sourceAnnotationType.getName(), alias.aliasedAttributeName,
                            alias.aliasedAnnotationType.getName());
                    throw new AnnotationConfigurationException(msg);
                }

                if (alias.isAliasPair) {
                    validateDefaultValueConfiguration(alias.aliasedAttribute);
                }
            }
        }

        private void validateDefaultValueConfiguration(Method aliasedAttribute) {
            Assert.notNull(aliasedAttribute, "aliasedAttribute must not be null");
            Object defaultValue = this.sourceAttribute.getDefaultValue();
            Object aliasedDefaultValue = aliasedAttribute.getDefaultValue();

            if (defaultValue == null || aliasedDefaultValue == null) {
                String msg = String.format("Misconfigured aliases: attribute '%s' in annotation [%s] " +
                                "and attribute '%s' in annotation [%s] must declare default values.",
                        this.sourceAttributeName, this.sourceAnnotationType.getName(), aliasedAttribute.getName(),
                        aliasedAttribute.getDeclaringClass().getName());
                throw new AnnotationConfigurationException(msg);
            }

            if (!ObjectUtils.nullSafeEquals(defaultValue, aliasedDefaultValue)) {
                String msg = String.format("Misconfigured aliases: attribute '%s' in annotation [%s] " +
                                "and attribute '%s' in annotation [%s] must declare the same default value.",
                        this.sourceAttributeName, this.sourceAnnotationType.getName(), aliasedAttribute.getName(),
                        aliasedAttribute.getDeclaringClass().getName());
                throw new AnnotationConfigurationException(msg);
            }
        }

        /**
         * Validate this descriptor against the supplied descriptor.
         * <p>This method only validates the configuration of default values
         * for the two descriptors, since other aspects of the descriptors
         * are validated when they are created.
         */
        private void validateAgainst(AliasDescriptor otherDescriptor) {
            validateDefaultValueConfiguration(otherDescriptor.sourceAttribute);
        }

        /**
         * Determine if this descriptor represents an explicit override for
         * an attribute in the supplied {@code metaAnnotationType}.
         *
         * @see #isAliasFor
         */
        private boolean isOverrideFor(Class<? extends Annotation> metaAnnotationType) {
            for (Alias alias : this.aliases) {
                if (alias.aliasedAnnotationType == metaAnnotationType) {
                    return true;
                }
            }
            return false;
        }

        /**
         * Determine if this descriptor and the supplied descriptor both
         * effectively represent aliases for the same attribute in the same
         * target annotation, either explicitly or implicitly.
         * <p>This method searches the attribute override hierarchy, beginning
         * with this descriptor, in order to detect implicit and transitively
         * implicit aliases.
         *
         * @return {@code true} if this descriptor and the supplied descriptor
         * effectively alias the same annotation attribute
         * @see #isOverrideFor
         */
        private boolean isAliasFor(AliasDescriptor otherDescriptor) {
            Queue<AliasDescriptor> lhs = new LinkedList<AliasDescriptor>();
            lhs.offer(this);
            while (!lhs.isEmpty()) {
                AliasDescriptor lh = lhs.poll();
                Queue<AliasDescriptor> rhs = new LinkedList<AliasDescriptor>();
                rhs.offer(otherDescriptor);
                while (!rhs.isEmpty()) {
                    AliasDescriptor rh = rhs.poll();
                    for (Alias lhAlias : lh.aliases) {
                        for (Alias rhAlias : rh.aliases) {
                            if (lhAlias.aliasedAttribute.equals(rhAlias.aliasedAttribute)) {
                                return true;
                            }
                        }
                    }
                    rhs.addAll(rh.getAttributeOverrideDescriptor());
                }
                lhs.addAll(lh.getAttributeOverrideDescriptor());
            }
            return false;
        }

        public List<String> getAttributeAliasNames() {
            // Explicit alias pair?
            List<String> aliases = new ArrayList<String>();
            for (Alias alias : this.aliases) {
                if (alias.isAliasPair) {
                    aliases.add(alias.aliasedAttributeName);
                }
            }

            // Else: search for implicit aliases
            for (AliasDescriptor otherDescriptor : getOtherDescriptors()) {
                if (this.isAliasFor(otherDescriptor)) {
                    this.validateAgainst(otherDescriptor);
                    aliases.add(otherDescriptor.sourceAttributeName);
                }
            }
            return aliases;
        }

        private List<AliasDescriptor> getOtherDescriptors() {
            List<AliasDescriptor> otherDescriptors = new ArrayList<AliasDescriptor>();
            for (Method currentAttribute : getAttributeMethods(this.sourceAnnotationType)) {
                if (!this.sourceAttribute.equals(currentAttribute)) {
                    AliasDescriptor otherDescriptor = AliasDescriptor.from(currentAttribute);
                    if (otherDescriptor != null) {
                        otherDescriptors.add(otherDescriptor);
                    }
                }
            }
            return otherDescriptors;
        }

        public String[] getAttributeOverrideName(Class<? extends Annotation> metaAnnotationType) {
            Assert.notNull(metaAnnotationType, "metaAnnotationType must not be null");
            Assert.isTrue(Annotation.class != metaAnnotationType,
                    "metaAnnotationType must not be [java.lang.annotation.Annotation]");
            List<String> result = new ArrayList<String>();
            Queue<AliasDescriptor> queue = new LinkedList<AliasDescriptor>();
            queue.offer(this);
            // Search the attribute override hierarchy, starting with the current attribute
            while (!queue.isEmpty()) {
                AliasDescriptor desc = queue.poll();
                if (desc == null) {
                    continue;
                }
                // Determine whether this descriptor represents an explicit
                // substitution of the attribute in the provided meta annotation.
                if (desc.aliases != null) {
                    for (int i = 0; i < desc.aliases.length; i++) {
                        if (desc.aliases[i].aliasedAnnotationType == metaAnnotationType) {
                            result.add(desc.aliases[i].aliasedAttributeName);
                        }
                    }
                }
                queue.addAll(desc.getAttributeOverrideDescriptor());
            }
            if (result.isEmpty()) {
                // Else: explicit attribute override for a different meta-annotation
                return null;
            }
            return result.toArray(new String[0]);
        }

        private List<AliasDescriptor> getAttributeOverrideDescriptor() {
            List<AliasDescriptor> result = new ArrayList<AliasDescriptor>();
            for (Alias alias : this.aliases) {
                Method aliasedAttribute = alias.aliasedAttribute;
                if (alias.isAliasPair) {
                    continue;
                }
                result.add(AliasDescriptor.from(aliasedAttribute));
            }
            return result;
        }

        /**
         * Get the name of the aliased attribute configured via the supplied
         * {@link MyAliasFor @AliasFor} annotation on the supplied {@code attribute},
         * or the original attribute if no aliased one specified (indicating that
         * the reference goes to a same-named attribute on a meta-annotation).
         * <p>This method returns the value of either the {@code attribute}
         * or {@code value} attribute of {@code @AliasFor}, ensuring that only
         * one of the attributes has been declared while simultaneously ensuring
         * that at least one of the attributes has been declared.
         *
         * @param aliasFor  the {@code @AliasFor} annotation from which to retrieve
         *                  the aliased attribute name
         * @param attribute the attribute that is annotated with {@code @AliasFor}
         * @return the name of the aliased attribute (never {@code null} or empty)
         * @throws AnnotationConfigurationException if invalid configuration of
         *                                          {@code @AliasFor} is detected
         */
        private String getAliasedAttributeName(MyAliasFor aliasFor, Method attribute) {
            String attributeName = aliasFor.attribute();
            String value = aliasFor.value();
            boolean attributeDeclared = StringUtils.hasText(attributeName);
            boolean valueDeclared = StringUtils.hasText(value);

            // Ensure user did not declare both 'value' and 'attribute' in @AliasFor
            if (attributeDeclared && valueDeclared) {
                String msg = String.format("In @AliasFor declared on attribute '%s' in annotation [%s], attribute 'attribute' " +
                                "and its alias 'value' are present with values of [%s] and [%s], but only one is permitted.",
                        attribute.getName(), attribute.getDeclaringClass().getName(), attributeName, value);
                throw new AnnotationConfigurationException(msg);
            }

            // Either explicit attribute name or pointing to same-named attribute by default
            attributeName = (attributeDeclared ? attributeName : value);
            return (StringUtils.hasText(attributeName) ? attributeName.trim() : attribute.getName());
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder(String.format("%s: @%s(%s) is an alias for [", getClass().getSimpleName(),
                    this.sourceAnnotationType.getSimpleName(), this.sourceAttributeName));
            for (int i = 0; i < this.aliases.length - 1; i++) {
                Alias alias = this.aliases[i];
                sb.append(String.format("@%s(%s)",
                        alias.aliasedAnnotationType.getSimpleName(), alias.aliasedAttributeName))
                        .append(",");
            }
            Alias last = this.aliases[this.aliases.length - 1];
            sb.append(String.format("@%s(%s)",
                    last.aliasedAnnotationType.getSimpleName(), last.aliasedAttributeName));
            sb.append("]");
            return sb.toString();
        }
    }
}
