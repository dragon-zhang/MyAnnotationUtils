/*
 * Copyright 2002-2019 the original author or authors.
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

import org.springframework.core.BridgeMethodResolver;
import org.springframework.core.annotation.MergedAnnotation.Adapt;
import org.springframework.lang.Nullable;
import org.springframework.util.MultiValueMap;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.util.Comparator;

/**
 * General utility methods for finding annotations, meta-annotations, and
 * repeatable annotations on {@link AnnotatedElement AnnotatedElements}.
 *
 * <p>{@code AnnotatedElementUtils} defines the public API for Spring's
 * meta-annotation programming model with support for <em>annotation attribute
 * overrides</em>. If you do not need support for annotation attribute
 * overrides, consider using {@link AnnotationUtils} instead.
 *
 * <p>Note that the features of this class are not provided by the JDK's
 * introspection facilities themselves.
 *
 * <h3>Annotation Attribute Overrides</h3>
 * <p>Support for meta-annotations with <em>attribute overrides</em> in
 * <em>composed annotations</em> is provided by all variants of the
 * {@code getMergedAnnotationAttributes()}, {@code getMergedAnnotation()},
 * {@code getAllMergedAnnotations()}, {@code getMergedRepeatableAnnotations()},
 * {@code findMergedAnnotationAttributes()}, {@code findMergedAnnotation()},
 * {@code findAllMergedAnnotations()}, and {@code findMergedRepeatableAnnotations()}
 * methods.
 *
 * <h3>Find vs. Get Semantics</h3>
 * <p>The search algorithms used by methods in this class follow either
 * <em>find</em> or <em>get</em> semantics. Consult the javadocs for each
 * individual method for details on which search algorithm is used.
 *
 * <p><strong>Get semantics</strong> are limited to searching for annotations
 * that are either <em>present</em> on an {@code AnnotatedElement} (i.e. declared
 * locally or {@linkplain java.lang.annotation.Inherited inherited}) or declared
 * within the annotation hierarchy <em>above</em> the {@code AnnotatedElement}.
 *
 * <p><strong>Find semantics</strong> are much more exhaustive, providing
 * <em>get semantics</em> plus support for the following:
 *
 * <ul>
 * <li>Searching on interfaces, if the annotated element is a class
 * <li>Searching on superclasses, if the annotated element is a class
 * <li>Resolving bridged methods, if the annotated element is a method
 * <li>Searching on methods in interfaces, if the annotated element is a method
 * <li>Searching on methods in superclasses, if the annotated element is a method
 * </ul>
 *
 * <h3>Support for {@code @Inherited}</h3>
 * <p>Methods following <em>get semantics</em> will honor the contract of Java's
 * {@link java.lang.annotation.Inherited @Inherited} annotation except that locally
 * declared annotations (including custom composed annotations) will be favored over
 * inherited annotations. In contrast, methods following <em>find semantics</em>
 * will completely ignore the presence of {@code @Inherited} since the <em>find</em>
 * search algorithm manually traverses type and method hierarchies and thereby
 * implicitly supports annotation inheritance without a need for {@code @Inherited}.
 *
 * <h3>Annotation Attribute Supporting Multiple Aliases</h3>
 * <p>As of Spring Framework 5.3, one annotation attribute supports the declaration
 * of multiple aliases. Use {@link #getMergedAnnotationWithMultipleAliases} to
 * get multiple aliases in an annotation attribute, instead of {@link #getMergedAnnotation}
 * For more information, see {@link MyAliasFors} please.
 *
 * @author Phillip Webb
 * @author Juergen Hoeller
 * @author Sam Brannen
 * @author ZiCheng Zhang
 * @since 4.0
 * @see MyAliasFor
 * @see AnnotationAttributes
 * @see AnnotationUtils
 * @see BridgeMethodResolver
 * @see MyAliasFors
 */
public abstract class MyAnnotatedElementUtils {

	/**
	 * Get the first annotation of the specified {@code annotationType} within
	 * the annotation hierarchy <em>above</em> the supplied {@code element},
	 * merge that annotation's attributes with <em>matching</em> attributes from
	 * annotations in lower levels of the annotation hierarchy, and synthesize
	 * the result back into an annotation of the specified {@code annotationType}.
	 * <p>{@link MyAliasFor @AliasFor} semantics are fully supported, both
	 * within a single annotation and within the annotation hierarchy.
	 *
	 * <h3>Annotation Attribute Supporting Multiple Aliases</h3>
	 * <p>As of Spring Framework 5.3, an annotation attribute can declare
	 * multiple aliases, for more detail ,see {@link MyAliasFors} please.
	 *
	 * @param element the annotated element
	 * @param annotationType the annotation type to find
	 * @return the merged, synthesized {@code Annotation}, or {@code null} if not found
	 * @since 5.3
	 * @see #findMergedAnnotation(AnnotatedElement, Class)
	 * @see MyAliasFors
	 */
	@Nullable
	public static <A extends Annotation> A getMergedAnnotationWithMultipleAliases(AnnotatedElement element, Class<A> annotationType) {
		// Shortcut: directly present on the element, with no merging needed?
		if (AnnotationFilter.PLAIN.matches(annotationType) ||
				AnnotationsScanner.hasPlainJavaAnnotationsOnly(element)) {
			return element.getDeclaredAnnotation(annotationType);
		}
		// Exhaustive retrieval of merged annotations...
		return getMultipleAliasesAnnotations(element)
				.get(annotationType, null, MergedAnnotationSelectors.firstDirectlyDeclared())
				.synthesize(MergedAnnotation::isPresent).orElse(null);
	}

	private static MergedAnnotations getMultipleAliasesAnnotations(AnnotatedElement element) {
		return MyMergedAnnotations.fromMultipleAliasesAnnotations(element, MergedAnnotations.SearchStrategy.INHERITED_ANNOTATIONS, RepeatableContainers.none());
	}

}
