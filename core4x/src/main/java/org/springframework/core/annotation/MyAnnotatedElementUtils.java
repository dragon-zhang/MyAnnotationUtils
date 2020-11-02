package org.springframework.core.annotation;

import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public abstract class MyAnnotatedElementUtils extends AnnotatedElementUtils {

    private static final Annotation[] EMPTY_ANNOTATION_ARRAY = new Annotation[0];

    /**
     * Get the first annotation of the specified {@code annotationType} within
     * the annotation hierarchy <em>above</em> the supplied {@code element},
     * merge that annotation's attributes with <em>matching</em> attributes from
     * annotations in lower levels of the annotation hierarchy, and synthesize
     * the result back into an annotation of the specified {@code annotationType}.
     * <p>{@link MyAliasFor @AliasFor} semantics are fully supported, both
     * within a single annotation and within the annotation hierarchy.
     * <p>This method delegates to {@link #getMergedAnnotationAttributes(AnnotatedElement, Class)}
     * and {@link MyAnnotationUtils#synthesizeAnnotation(Map, Class, AnnotatedElement)}.
     *
     * @param element        the annotated element
     * @param annotationType the annotation type to find
     * @return the merged, synthesized {@code Annotation}, or {@code null} if not found
     * @see #getMergedAnnotationAttributes(AnnotatedElement, Class)
     * @see #findMergedAnnotation(AnnotatedElement, Class)
     * @see MyAnnotationUtils#synthesizeAnnotation(Map, Class, AnnotatedElement)
     * @since 4.2
     */
    public static <A extends Annotation> A getMergedAnnotation(AnnotatedElement element, Class<A> annotationType) {
        Assert.notNull(annotationType, "'annotationType' must not be null");

        // Shortcut: directly present on the element, with no merging needed?
        if (!(element instanceof Class)) {
            // Do not use this shortcut against a Class: Inherited annotations
            // would get preferred over locally declared composed annotations.
            A annotation = element.getAnnotation(annotationType);
            if (annotation != null) {
                return MyAnnotationUtils.synthesizeAnnotation(annotation, element);
            }
        }

        // Exhaustive retrieval of merged annotation attributes...
        AnnotationAttributes attributes = getMergedAnnotationAttributes(element, annotationType);
        return MyAnnotationUtils.synthesizeAnnotation(attributes, annotationType, element);
    }

    /**
     * Get the first annotation of the specified {@code annotationType} within
     * the annotation hierarchy <em>above</em> the supplied {@code element} and
     * merge that annotation's attributes with <em>matching</em> attributes from
     * annotations in lower levels of the annotation hierarchy.
     * <p>{@link MyAliasFor @AliasFor} semantics are fully supported, both
     * within a single annotation and within the annotation hierarchy.
     * <p>This method delegates to {@link #getMergedAnnotationAttributes(AnnotatedElement, String)}.
     *
     * @param element        the annotated element
     * @param annotationType the annotation type to find
     * @return the merged {@code AnnotationAttributes}, or {@code null} if not found
     * @see #getMergedAnnotationAttributes(AnnotatedElement, String, boolean, boolean)
     * @see #findMergedAnnotationAttributes(AnnotatedElement, String, boolean, boolean)
     * @see #getMergedAnnotation(AnnotatedElement, Class)
     * @see #findMergedAnnotation(AnnotatedElement, Class)
     * @since 4.2
     */
    public static AnnotationAttributes getMergedAnnotationAttributes(
            AnnotatedElement element, Class<? extends Annotation> annotationType) {

        Assert.notNull(annotationType, "'annotationType' must not be null");
        AnnotationAttributes attributes = searchWithGetSemantics(element, annotationType, null,
                new MyAnnotatedElementUtils.MergedAnnotationAttributesProcessor());
        MyAnnotationUtils.postProcessAnnotationAttributes(element, attributes, false, false);
        return attributes;
    }

    /**
     * Search for annotations of the specified {@code annotationName} or
     * {@code annotationType} on the specified {@code element}, following
     * <em>get semantics</em>.
     *
     * @param element        the annotated element
     * @param annotationType the annotation type to find
     * @param annotationName the fully qualified class name of the annotation
     *                       type to find (as an alternative to {@code annotationType})
     * @param processor      the processor to delegate to
     * @return the result of the processor (potentially {@code null})
     */
    private static <T> T searchWithGetSemantics(AnnotatedElement element,
                                                Class<? extends Annotation> annotationType, String annotationName, Processor<T> processor) {

        return searchWithGetSemantics(element, annotationType, annotationName, null, processor);
    }


    /**
     * Search for annotations of the specified {@code annotationName} or
     * {@code annotationType} on the specified {@code element}, following
     * <em>get semantics</em>.
     *
     * @param element        the annotated element
     * @param annotationType the annotation type to find
     * @param annotationName the fully qualified class name of the annotation
     *                       type to find (as an alternative to {@code annotationType})
     * @param containerType  the type of the container that holds repeatable
     *                       annotations, or {@code null} if the annotation is not repeatable
     * @param processor      the processor to delegate to
     * @return the result of the processor (potentially {@code null})
     * @since 4.3
     */
    private static <T> T searchWithGetSemantics(AnnotatedElement element,
                                                Class<? extends Annotation> annotationType, String annotationName,
                                                Class<? extends Annotation> containerType, Processor<T> processor) {

        try {
            return searchWithGetSemantics(element, annotationType, annotationName,
                    containerType, processor, new HashSet<AnnotatedElement>(), 0);
        } catch (Throwable ex) {
            MyAnnotationUtils.rethrowAnnotationConfigurationException(ex);
            throw new IllegalStateException("Failed to introspect annotations on " + element, ex);
        }
    }


    /**
     * Perform the search algorithm for the {@link #searchWithGetSemantics}
     * method, avoiding endless recursion by tracking which annotated elements
     * have already been <em>visited</em>.
     * <p>The {@code metaDepth} parameter is explained in the
     * {@link Processor#process process()} method of the {@link Processor} API.
     *
     * @param element        the annotated element
     * @param annotationType the annotation type to find
     * @param annotationName the fully qualified class name of the annotation
     *                       type to find (as an alternative to {@code annotationType})
     * @param containerType  the type of the container that holds repeatable
     *                       annotations, or {@code null} if the annotation is not repeatable
     * @param processor      the processor to delegate to
     * @param visited        the set of annotated elements that have already been visited
     * @param metaDepth      the meta-depth of the annotation
     * @return the result of the processor (potentially {@code null})
     */
    private static <T> T searchWithGetSemantics(AnnotatedElement element,
                                                Class<? extends Annotation> annotationType, String annotationName,
                                                Class<? extends Annotation> containerType, Processor<T> processor,
                                                Set<AnnotatedElement> visited, int metaDepth) {

        Assert.notNull(element, "AnnotatedElement must not be null");

        if (visited.add(element)) {
            try {
                // Start searching within locally declared annotations
                List<Annotation> declaredAnnotations = Arrays.asList(element.getDeclaredAnnotations());
                T result = searchWithGetSemanticsInAnnotations(element, declaredAnnotations,
                        annotationType, annotationName, containerType, processor, visited, metaDepth);
                if (result != null) {
                    return result;
                }

                if (element instanceof Class) {  // otherwise getAnnotations does not return anything new
                    List<Annotation> inheritedAnnotations = new ArrayList<Annotation>();
                    for (Annotation annotation : element.getAnnotations()) {
                        if (!declaredAnnotations.contains(annotation)) {
                            inheritedAnnotations.add(annotation);
                        }
                    }

                    // Continue searching within inherited annotations
                    result = searchWithGetSemanticsInAnnotations(element, inheritedAnnotations,
                            annotationType, annotationName, containerType, processor, visited, metaDepth);
                    if (result != null) {
                        return result;
                    }
                }
            } catch (Throwable ex) {
                MyAnnotationUtils.handleIntrospectionFailure(element, ex);
            }
        }

        return null;
    }


    /**
     * This method is invoked by {@link #searchWithGetSemantics} to perform
     * the actual search within the supplied list of annotations.
     * <p>This method should be invoked first with locally declared annotations
     * and then subsequently with inherited annotations, thereby allowing
     * local annotations to take precedence over inherited annotations.
     * <p>The {@code metaDepth} parameter is explained in the
     * {@link Processor#process process()} method of the {@link Processor} API.
     *
     * @param element        the element that is annotated with the supplied
     *                       annotations, used for contextual logging; may be {@code null} if unknown
     * @param annotations    the annotations to search in
     * @param annotationType the annotation type to find
     * @param annotationName the fully qualified class name of the annotation
     *                       type to find (as an alternative to {@code annotationType})
     * @param containerType  the type of the container that holds repeatable
     *                       annotations, or {@code null} if the annotation is not repeatable
     * @param processor      the processor to delegate to
     * @param visited        the set of annotated elements that have already been visited
     * @param metaDepth      the meta-depth of the annotation
     * @return the result of the processor (potentially {@code null})
     * @since 4.2
     */
    private static <T> T searchWithGetSemanticsInAnnotations(AnnotatedElement element,
                                                             List<Annotation> annotations, Class<? extends Annotation> annotationType,
                                                             String annotationName, Class<? extends Annotation> containerType,
                                                             Processor<T> processor, Set<AnnotatedElement> visited, int metaDepth) {

        // Search in annotations
        for (Annotation annotation : annotations) {
            Class<? extends Annotation> currentAnnotationType = annotation.annotationType();
            if (!MyAnnotationUtils.isInJavaLangAnnotationPackage(currentAnnotationType)) {
                if (currentAnnotationType == annotationType ||
                        currentAnnotationType.getName().equals(annotationName) ||
                        processor.alwaysProcesses()) {
                    T result = processor.process(element, annotation, metaDepth);
                    if (result != null) {
                        if (processor.aggregates() && metaDepth == 0) {
                            processor.getAggregatedResults().add(result);
                        } else {
                            return result;
                        }
                    }
                }
                // Repeatable annotations in container?
                else if (currentAnnotationType == containerType) {
                    for (Annotation contained : getRawAnnotationsFromContainer(element, annotation)) {
                        T result = processor.process(element, contained, metaDepth);
                        if (result != null) {
                            // No need to post-process since repeatable annotations within a
                            // container cannot be composed annotations.
                            processor.getAggregatedResults().add(result);
                        }
                    }
                }
            }
        }

        // Recursively search in meta-annotations
        for (Annotation annotation : annotations) {
            Class<? extends Annotation> currentAnnotationType = annotation.annotationType();
            if (!MyAnnotationUtils.isInJavaLangAnnotationPackage(currentAnnotationType)) {
                T result = searchWithGetSemantics(currentAnnotationType, annotationType,
                        annotationName, containerType, processor, visited, metaDepth + 1);
                if (result != null) {
                    processor.postProcess(element, annotation, result);
                    if (processor.aggregates() && metaDepth == 0) {
                        processor.getAggregatedResults().add(result);
                    } else {
                        return result;
                    }
                }
            }
        }

        return null;
    }


    /**
     * Get the array of raw (unsynthesized) annotations from the {@code value}
     * attribute of the supplied repeatable annotation {@code container}.
     *
     * @since 4.3
     */
    @SuppressWarnings("unchecked")
    private static <A extends Annotation> A[] getRawAnnotationsFromContainer(AnnotatedElement element,
                                                                             Annotation container) {

        try {
            return (A[]) MyAnnotationUtils.getValue(container);
        } catch (Throwable ex) {
            MyAnnotationUtils.handleIntrospectionFailure(element, ex);
        }
        // Unable to read value from repeating annotation container -> ignore it.
        return (A[]) EMPTY_ANNOTATION_ARRAY;
    }

    /**
     * Callback interface that is used to process annotations during a search.
     * <p>Depending on the use case, a processor may choose to
     * {@linkplain #process} a single target annotation, multiple target
     * annotations, or all annotations discovered by the currently executing
     * search. The term "target" in this context refers to a matching
     * annotation (i.e., a specific annotation type that was found during
     * the search).
     * <p>Returning a non-null value from the {@link #process}
     * method instructs the search algorithm to stop searching further;
     * whereas, returning {@code null} from the {@link #process} method
     * instructs the search algorithm to continue searching for additional
     * annotations. One exception to this rule applies to processors
     * that {@linkplain #aggregates aggregate} results. If an aggregating
     * processor returns a non-null value, that value will be added to the
     * list of {@linkplain #getAggregatedResults aggregated results}
     * and the search algorithm will continue.
     * <p>Processors can optionally {@linkplain #postProcess post-process}
     * the result of the {@link #process} method as the search algorithm
     * goes back down the annotation hierarchy from an invocation of
     * {@link #process} that returned a non-null value down to the
     * {@link AnnotatedElement} that was supplied as the starting point to
     * the search algorithm.
     *
     * @param <T> the type of result returned by the processor
     */
    private interface Processor<T> {

        /**
         * Process the supplied annotation.
         * <p>The supplied annotation will be an actual target annotation
         * that has been found by the search algorithm, unless this processor
         * is configured to {@linkplain #alwaysProcesses always process}
         * annotations in which case it may be some other annotation within an
         * annotation hierarchy. In the latter case, the {@code metaDepth}
         * will have a value greater than {@code 0}. In any case, it is
         * up to concrete implementations of this method to decide what to
         * do with the supplied annotation.
         * <p>The {@code metaDepth} parameter represents the depth of the
         * annotation relative to the first annotated element in the
         * annotation hierarchy. For example, an annotation that is
         * <em>present</em> on a non-annotation element will have a depth
         * of 0; a meta-annotation will have a depth of 1; and a
         * meta-meta-annotation will have a depth of 2; etc.
         *
         * @param annotatedElement the element that is annotated with the
         *                         supplied annotation, used for contextual logging; may be
         *                         {@code null} if unknown
         * @param annotation       the annotation to process
         * @param metaDepth        the meta-depth of the annotation
         * @return the result of the processing, or {@code null} to continue
         * searching for additional annotations
         */
        T process(AnnotatedElement annotatedElement, Annotation annotation, int metaDepth);

        /**
         * Post-process the result returned by the {@link #process} method.
         * <p>The {@code annotation} supplied to this method is an annotation
         * that is present in the annotation hierarchy, between the initial
         * {@link AnnotatedElement} and an invocation of {@link #process}
         * that returned a non-null value.
         *
         * @param annotatedElement the element that is annotated with the
         *                         supplied annotation, used for contextual logging; may be
         *                         {@code null} if unknown
         * @param annotation       the annotation to post-process
         * @param result           the result to post-process
         */
        void postProcess(AnnotatedElement annotatedElement, Annotation annotation, T result);

        /**
         * Determine if this processor always processes annotations regardless of
         * whether or not the target annotation has been found.
         *
         * @return {@code true} if this processor always processes annotations
         * @since 4.3
         */
        boolean alwaysProcesses();

        /**
         * Determine if this processor aggregates the results returned by {@link #process}.
         * <p>If this method returns {@code true}, then {@link #getAggregatedResults()}
         * must return a non-null value.
         *
         * @return {@code true} if this processor supports aggregated results
         * @see #getAggregatedResults
         * @since 4.3
         */
        boolean aggregates();

        /**
         * Get the list of results aggregated by this processor.
         * <p>NOTE: the processor does <strong>not</strong> aggregate the results
         * itself. Rather, the search algorithm that uses this processor is
         * responsible for asking this processor if it {@link #aggregates} results
         * and then adding the post-processed results to the list returned by this
         * method.
         *
         * @return the list of results aggregated by this processor (never {@code null})
         * @see #aggregates
         * @since 4.3
         */
        List<T> getAggregatedResults();
    }

    /**
     * {@link Processor} that gets the {@code AnnotationAttributes} for the
     * target annotation during the {@link #process} phase and then merges
     * annotation attributes from lower levels in the annotation hierarchy
     * during the {@link #postProcess} phase.
     * <p>A {@code MergedAnnotationAttributesProcessor} may optionally be
     * configured to {@linkplain #aggregates aggregate} results.
     *
     * @see MyAnnotationUtils#retrieveAnnotationAttributes
     * @see MyAnnotationUtils#postProcessAnnotationAttributes
     * @since 4.2
     */
    private static class MergedAnnotationAttributesProcessor implements Processor<AnnotationAttributes> {

        private final boolean classValuesAsString;

        private final boolean nestedAnnotationsAsMap;

        private final boolean aggregates;

        private final List<AnnotationAttributes> aggregatedResults;

        MergedAnnotationAttributesProcessor() {
            this(false, false, false);
        }

        MergedAnnotationAttributesProcessor(boolean classValuesAsString, boolean nestedAnnotationsAsMap) {
            this(classValuesAsString, nestedAnnotationsAsMap, false);
        }

        MergedAnnotationAttributesProcessor(boolean classValuesAsString, boolean nestedAnnotationsAsMap,
                                            boolean aggregates) {

            this.classValuesAsString = classValuesAsString;
            this.nestedAnnotationsAsMap = nestedAnnotationsAsMap;
            this.aggregates = aggregates;
            this.aggregatedResults = (aggregates ? new ArrayList<AnnotationAttributes>() : null);
        }

        @Override
        public boolean alwaysProcesses() {
            return false;
        }

        @Override
        public boolean aggregates() {
            return this.aggregates;
        }

        @Override
        public List<AnnotationAttributes> getAggregatedResults() {
            return this.aggregatedResults;
        }

        @Override
        public AnnotationAttributes process(AnnotatedElement annotatedElement, Annotation annotation, int metaDepth) {
            return MyAnnotationUtils.retrieveAnnotationAttributes(annotatedElement, annotation,
                    this.classValuesAsString, this.nestedAnnotationsAsMap);
        }

        @Override
        public void postProcess(AnnotatedElement element, Annotation annotation, AnnotationAttributes attributes) {
            annotation = MyAnnotationUtils.synthesizeAnnotation(annotation, element);
            Class<? extends Annotation> targetAnnotationType = attributes.annotationType();

            // Track which attribute values have already been replaced so that we can short
            // circuit the search algorithms.
            Set<String> valuesAlreadyReplaced = new HashSet<String>();

            for (Method attributeMethod : MyAnnotationUtils.getAttributeMethods(annotation.annotationType())) {
                String attributeName = attributeMethod.getName();
                String[] attributeOverrideNames = MyAnnotationUtils.getAttributeOverrideName(targetAnnotationType, attributeMethod);
                if (null == attributeOverrideNames) {
                    continue;
                }
                for (String attributeOverrideName : attributeOverrideNames) {
                    // Explicit annotation attribute override declared via @AliasFor
                    if (!StringUtils.isEmpty(attributeOverrideName)) {
                        if (valuesAlreadyReplaced.contains(attributeOverrideName)) {
                            continue;
                        }

                        List<String> targetAttributeNames = new ArrayList<String>();
                        targetAttributeNames.add(attributeOverrideName);
                        valuesAlreadyReplaced.add(attributeOverrideName);

                        // Ensure all aliased attributes in the target annotation are overridden. (SPR-14069)
                        List<String> aliases = MyAnnotationUtils.getAttributeAliasMap(targetAnnotationType).get(attributeOverrideNames);
                        if (aliases != null) {
                            for (String alias : aliases) {
                                if (!valuesAlreadyReplaced.contains(alias)) {
                                    targetAttributeNames.add(alias);
                                    valuesAlreadyReplaced.add(alias);
                                }
                            }
                        }

                        overrideAttributes(element, annotation, attributes, attributeName, targetAttributeNames);
                    }
                    // Implicit annotation attribute override based on convention
                    else if (!MyAnnotationUtils.VALUE.equals(attributeName) && attributes.containsKey(attributeName)) {
                        overrideAttribute(element, annotation, attributes, attributeName, attributeName);
                    }
                }
            }
        }

        private void overrideAttributes(AnnotatedElement element, Annotation annotation,
                                        AnnotationAttributes attributes, String sourceAttributeName, List<String> targetAttributeNames) {

            Object adaptedValue = getAdaptedValue(element, annotation, sourceAttributeName);

            for (String targetAttributeName : targetAttributeNames) {
                attributes.put(targetAttributeName, adaptedValue);
            }
        }

        private void overrideAttribute(AnnotatedElement element, Annotation annotation, AnnotationAttributes attributes,
                                       String sourceAttributeName, String targetAttributeName) {

            attributes.put(targetAttributeName, getAdaptedValue(element, annotation, sourceAttributeName));
        }

        private Object getAdaptedValue(AnnotatedElement element, Annotation annotation, String sourceAttributeName) {
            Object value = MyAnnotationUtils.getValue(annotation, sourceAttributeName);
            return MyAnnotationUtils.adaptValue(element, value, this.classValuesAsString, this.nestedAnnotationsAsMap);
        }
    }
}
