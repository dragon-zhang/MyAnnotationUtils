/*
 * Copyright 2002-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.core.annotation;

import org.springframework.lang.Nullable;
import org.springframework.util.ConcurrentReferenceHashMap;

import java.lang.annotation.Annotation;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

/**
 * Provides {@link MyAnnotationTypeMapping} information for a single source
 * annotation type. Performs a recursive breadth first crawl of all
 * meta-annotations to ultimately provide a quick way to map the attributes of
 * a root {@link Annotation}.
 *
 * <p>Supports convention based merging of meta-annotations as well as implicit
 * and explicit {@link MyAliasFor @AliasFor} aliases. Also provides information
 * about mirrored attributes.
 *
 * <p>This class is designed to be cached so that meta-annotations only need to
 * be searched once, regardless of how many times they are actually used.
 *
 * @author Phillip Webb
 * @author ZiCheng Zhang
 * @since 5.2
 * @see MyAnnotationTypeMapping
 * @see MyAliasFors
 */
final class MyAnnotationTypeMappings {

	private static final IntrospectionFailureLogger failureLogger = IntrospectionFailureLogger.DEBUG;

	private static final Map<AnnotationFilter, Cache> standardRepeatablesCache = new ConcurrentReferenceHashMap<>();

	private static final Map<AnnotationFilter, Cache> noRepeatablesCache = new ConcurrentReferenceHashMap<>();


	private final RepeatableContainers repeatableContainers;

	private final AnnotationFilter filter;

	private final List<MyAnnotationTypeMapping> mappings;


	private MyAnnotationTypeMappings(RepeatableContainers repeatableContainers,
									 AnnotationFilter filter, Class<? extends Annotation> annotationType) {

		this.repeatableContainers = repeatableContainers;
		this.filter = filter;
		this.mappings = new ArrayList<>();
		addAllMappings(annotationType);
		this.mappings.forEach(MyAnnotationTypeMapping::afterAllMappingsSet);
	}

	/**
	 * todo
	 * This constructor is compatible with the case of only declaring a single alias.
	 * It should be replaced directly in the future
	 * {@link #MyAnnotationTypeMappings(RepeatableContainers, AnnotationFilter, Class)},
	 * and there is no need to pass in {@code boolean enableMultipleAliases}
	 */
	private MyAnnotationTypeMappings(RepeatableContainers repeatableContainers, AnnotationFilter filter,
									 Class<? extends Annotation> annotationType, boolean enableMultipleAliases) {
		this.repeatableContainers = repeatableContainers;
		this.filter = filter;
		this.mappings = new ArrayList<>();
		addAllMappings(annotationType, enableMultipleAliases);
		this.mappings.forEach(MyAnnotationTypeMapping::afterAllMappingsSet);
	}

	private void addAllMappings(Class<? extends Annotation> annotationType) {
		Deque<MyAnnotationTypeMapping> queue = new ArrayDeque<>();
		addIfPossible(queue, null, annotationType, null);
		while (!queue.isEmpty()) {
			MyAnnotationTypeMapping mapping = queue.removeFirst();
			this.mappings.add(mapping);
			addMetaAnnotationsToQueue(queue, mapping);
		}
	}

	private void addAllMappings(Class<? extends Annotation> annotationType, boolean enableMultipleAliases) {
		Deque<MyAnnotationTypeMapping> queue = new ArrayDeque<>();
		addIfPossible(queue, null, annotationType, null, enableMultipleAliases);
		while (!queue.isEmpty()) {
			MyAnnotationTypeMapping mapping = queue.removeFirst();
			this.mappings.add(mapping);
			addMetaAnnotationsToQueue(queue, mapping, enableMultipleAliases);
		}
	}

	private void addMetaAnnotationsToQueue(Deque<MyAnnotationTypeMapping> queue, MyAnnotationTypeMapping source, boolean enableMultipleAliases) {
		Annotation[] metaAnnotations = AnnotationsScanner.getDeclaredAnnotations(source.getAnnotationType(), false);
		for (Annotation metaAnnotation : metaAnnotations) {
			if (!isMappable(source, metaAnnotation)) {
				continue;
			}
			Annotation[] repeatedAnnotations = this.repeatableContainers.findRepeatedAnnotations(metaAnnotation);
			if (repeatedAnnotations != null) {
				for (Annotation repeatedAnnotation : repeatedAnnotations) {
					if (!isMappable(source, repeatedAnnotation)) {
						continue;
					}
					addIfPossible(queue, source, repeatedAnnotation, enableMultipleAliases);
				}
			}
			else {
				addIfPossible(queue, source, metaAnnotation, enableMultipleAliases);
			}
		}
	}

	private void addMetaAnnotationsToQueue(Deque<MyAnnotationTypeMapping> queue, MyAnnotationTypeMapping source) {
		Annotation[] metaAnnotations = AnnotationsScanner.getDeclaredAnnotations(source.getAnnotationType(), false);
		for (Annotation metaAnnotation : metaAnnotations) {
			if (!isMappable(source, metaAnnotation)) {
				continue;
			}
			Annotation[] repeatedAnnotations = this.repeatableContainers.findRepeatedAnnotations(metaAnnotation);
			if (repeatedAnnotations != null) {
				for (Annotation repeatedAnnotation : repeatedAnnotations) {
					if (!isMappable(source, repeatedAnnotation)) {
						continue;
					}
					addIfPossible(queue, source, repeatedAnnotation);
				}
			}
			else {
				addIfPossible(queue, source, metaAnnotation);
			}
		}
	}

	private void addIfPossible(Deque<MyAnnotationTypeMapping> queue, MyAnnotationTypeMapping source, Annotation ann) {
		addIfPossible(queue, source, ann.annotationType(), ann);
	}

	private void addIfPossible(Deque<MyAnnotationTypeMapping> queue, @Nullable MyAnnotationTypeMapping source,
							   Class<? extends Annotation> annotationType, @Nullable Annotation ann) {

		try {
			queue.addLast(new MyAnnotationTypeMapping(source, annotationType, ann));
		}
		catch (Exception ex) {
			AnnotationUtils.rethrowAnnotationConfigurationException(ex);
			if (failureLogger.isEnabled()) {
				failureLogger.log("Failed to introspect meta-annotation " + annotationType.getName(),
						(source != null ? source.getAnnotationType() : null), ex);
			}
		}
	}

	private void addIfPossible(Deque<MyAnnotationTypeMapping> queue, MyAnnotationTypeMapping source, Annotation ann, boolean enableMultipleAliases) {
		addIfPossible(queue, source, ann.annotationType(), ann, enableMultipleAliases);
	}

	private void addIfPossible(Deque<MyAnnotationTypeMapping> queue, @Nullable MyAnnotationTypeMapping source,
							   Class<? extends Annotation> annotationType, @Nullable Annotation ann,
							   boolean enableMultipleAliases) {
		try {
			queue.addLast(new MyAnnotationTypeMapping(source, annotationType, ann, enableMultipleAliases));
		}
		catch (Exception ex) {
			AnnotationUtils.rethrowAnnotationConfigurationException(ex);
			if (failureLogger.isEnabled()) {
				failureLogger.log("Failed to introspect meta-annotation " + annotationType.getName(),
						(source != null ? source.getAnnotationType() : null), ex);
			}
		}
	}

	private boolean isMappable(MyAnnotationTypeMapping source, @Nullable Annotation metaAnnotation) {
		return (metaAnnotation != null && !this.filter.matches(metaAnnotation) &&
				!AnnotationFilter.PLAIN.matches(source.getAnnotationType()) &&
				!isAlreadyMapped(source, metaAnnotation));
	}

	private boolean isAlreadyMapped(MyAnnotationTypeMapping source, Annotation metaAnnotation) {
		Class<? extends Annotation> annotationType = metaAnnotation.annotationType();
		MyAnnotationTypeMapping mapping = source;
		while (mapping != null) {
			if (mapping.getAnnotationType() == annotationType) {
				return true;
			}
			mapping = mapping.getSource();
		}
		return false;
	}

	/**
	 * Get the total number of contained mappings.
	 * @return the total number of mappings
	 */
	int size() {
		return this.mappings.size();
	}

	/**
	 * Get an individual mapping from this instance.
	 * <p>Index {@code 0} will always return the root mapping; higher indexes
	 * will return meta-annotation mappings.
	 * @param index the index to return
	 * @return the {@link MyAnnotationTypeMapping}
	 * @throws IndexOutOfBoundsException if the index is out of range
	 * (<tt>index &lt; 0 || index &gt;= size()</tt>)
	 */
	MyAnnotationTypeMapping get(int index) {
		return this.mappings.get(index);
	}


	/**
	 * Create {@link MyAnnotationTypeMappings} for the specified annotation type.
	 * @param annotationType the source annotation type
	 * @return type mappings for the annotation type
	 */
	static MyAnnotationTypeMappings forAnnotationType(Class<? extends Annotation> annotationType) {
		return forAnnotationType(annotationType, AnnotationFilter.PLAIN);
	}

	/**
	 * Create {@link MyAnnotationTypeMappings} for the specified annotation type.
	 * @param annotationType the source annotation type
	 * @param annotationFilter the annotation filter used to limit which
	 * annotations are considered
	 * @return type mappings for the annotation type
	 */
	static MyAnnotationTypeMappings forAnnotationType(
			Class<? extends Annotation> annotationType, AnnotationFilter annotationFilter) {

		return forAnnotationType(annotationType, RepeatableContainers.standardRepeatables(), annotationFilter);
	}

	/**
	 * Create {@link MyAnnotationTypeMappings} for the specified annotation type.
	 * @param annotationType the source annotation type
	 * @param repeatableContainers the repeatable containers that may be used by
	 * the meta-annotations
	 * @param annotationFilter the annotation filter used to limit which
	 * annotations are considered
	 * @return type mappings for the annotation type
	 */
	static MyAnnotationTypeMappings forAnnotationType(Class<? extends Annotation> annotationType,
													  RepeatableContainers repeatableContainers, AnnotationFilter annotationFilter) {

		if (repeatableContainers == RepeatableContainers.standardRepeatables()) {
			return standardRepeatablesCache.computeIfAbsent(annotationFilter,
					key -> new Cache(repeatableContainers, key)).get(annotationType);
		}
		if (repeatableContainers == RepeatableContainers.none()) {
			return noRepeatablesCache.computeIfAbsent(annotationFilter,
					key -> new Cache(repeatableContainers, key)).get(annotationType);
		}
		return new MyAnnotationTypeMappings(repeatableContainers, annotationFilter, annotationType);
	}

	/**
	 * Create {@link MyAnnotationTypeMappings} for the specified annotation type.
	 *
	 * <h3>Annotation Attribute Supporting Multiple Aliases</h3>
	 * <p>As of Spring Framework 5.3, one annotation attribute supports
	 * the declaration of multiple aliases. For more information, see
	 * {@link MyAliasFors} please.
	 *
	 * @param annotationType the source annotation type
	 * @param repeatableContainers the repeatable containers that may be used by
	 * the meta-annotations
	 * @param annotationFilter the annotation filter used to limit which
	 * annotations are considered
	 * @return type mappings for the annotation type
	 */
	static MyAnnotationTypeMappings forMultipleAnnotationType(Class<? extends Annotation> annotationType,
															  RepeatableContainers repeatableContainers, AnnotationFilter annotationFilter) {
		if (repeatableContainers == RepeatableContainers.standardRepeatables()) {
			return standardRepeatablesCache.computeIfAbsent(annotationFilter,
					key -> new Cache(repeatableContainers, key)).batchGet(annotationType);
		}
		if (repeatableContainers == RepeatableContainers.none()) {
			return noRepeatablesCache.computeIfAbsent(annotationFilter,
					key -> new Cache(repeatableContainers, key)).batchGet(annotationType);
		}
		return new MyAnnotationTypeMappings(repeatableContainers, annotationFilter, annotationType, true);
	}

	static void clearCache() {
		standardRepeatablesCache.clear();
		noRepeatablesCache.clear();
	}


	/**
	 * Cache created per {@link AnnotationFilter}.
	 */
	private static class Cache {

		private final RepeatableContainers repeatableContainers;

		private final AnnotationFilter filter;

		private final Map<Class<? extends Annotation>, MyAnnotationTypeMappings> mappings;

		/**
		 * Create a cache instance with the specified filter.
		 * @param filter the annotation filter
		 */
		Cache(RepeatableContainers repeatableContainers, AnnotationFilter filter) {
			this.repeatableContainers = repeatableContainers;
			this.filter = filter;
			this.mappings = new ConcurrentReferenceHashMap<>();
		}

		/**
		 * Get or create {@link MyAnnotationTypeMappings} for the specified annotation type.
		 * Supporting multiple aliases.
		 * @param annotationType the annotation type
		 * @return a new or existing {@link MyAnnotationTypeMappings} instance
		 */
		MyAnnotationTypeMappings batchGet(Class<? extends Annotation> annotationType) {
			return this.mappings.computeIfAbsent(annotationType, this::batchCreateMappings);
		}

		/**
		 * Get or create {@link MyAnnotationTypeMappings} for the specified annotation type.
		 * @param annotationType the annotation type
		 * @return a new or existing {@link MyAnnotationTypeMappings} instance
		 */
		MyAnnotationTypeMappings get(Class<? extends Annotation> annotationType) {
			return this.mappings.computeIfAbsent(annotationType, this::createMappings);
		}

		MyAnnotationTypeMappings createMappings(Class<? extends Annotation> annotationType) {
			return new MyAnnotationTypeMappings(this.repeatableContainers, this.filter, annotationType);
		}

		/**
		 * Supporting multiple aliases.
		 */
		MyAnnotationTypeMappings batchCreateMappings(Class<? extends Annotation> annotationType) {
			return new MyAnnotationTypeMappings(this.repeatableContainers, this.filter, annotationType,true);
		}
	}

}
