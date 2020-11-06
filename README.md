# MyAnnotationUtils

作者对Spring提供的`@AliasFor`进行了扩展，尝试贡献给Spring官方(见下方的提交链接)：

Spring4提交记录：https://github.com/spring-projects/spring-framework/pull/25592

Spring5提交记录：https://github.com/spring-projects/spring-framework/pull/25857

人过留名，雁过留声。

在此记录一下，虽然最终没有被官方采纳，但是仍不妨碍开源出来给各位使用，来看下作者扩展了什么吧。

👇👇👇👇👇👇👇👇👇👇👇👇👇👇👇👇👇👇👇👇👇👇👇👇👇👇👇👇👇👇👇👇👇👇👇👇👇

Spring提供的`@AliasFor`，一个注释属性仅支持一个别名，您只能这样做：

```java
@Target({ElementType.ANNOTATION_TYPE, ElementType.FIELD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Test1 {
    String test1() default "test1";
}

@Target({ElementType.ANNOTATION_TYPE, ElementType.FIELD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Test2 {
        String test2() default "test2";
}

@Target({ElementType.ANNOTATION_TYPE, ElementType.FIELD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Test1
@Test2
public @interface Test3 {

    @AliasFor(annotation = Test1.class, attribute = "test1")
    String test3() default "test3";
    
    @AliasFor(annotation = Test2.class, attribute = "test2")
    String test4() default "test4";
}
```

但是现在，引入依赖：

Spring4
```xml
<dependency>
    <groupId>dragon.springframework</groupId>
    <artifactId>core4x</artifactId>
    <version>1.0.0</version>
</dependency>
```
Spring5
```xml
<dependency>
    <groupId>dragon.springframework</groupId>
    <artifactId>core5x</artifactId>
    <version>1.0.0</version>
</dependency>
```

使用`@MyAliasFor`，您可以更轻松地做到这一点：

```java
@Target({ElementType.ANNOTATION_TYPE, ElementType.FIELD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Test1
@Test2
public @interface Test3 {

    @MyAliasFor(annotation = Test1.class, attribute = "test1")
    @MyAliasFor(annotation = Test2.class, attribute = "test2")
    String test3() default "test3";
}
```

此外，同一注解中不同属性的相互别名可以打破最初的限制——只支持两个属性互为别名：

```java
@Target({ElementType.ANNOTATION_TYPE, ElementType.FIELD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Test4 {

    @MyAliasFor("test2")
    @MyAliasFor("test3")
    String test1() default "test";

    @MyAliasFor("test1")
    @MyAliasFor("test3")
    String test2() default "test";

    @MyAliasFor("test1")
    @MyAliasFor("test2")
    String test3() default "test";
}
```

结合上面的示例，您可以像下面这样来使用`@MyAliasFor`：

```java
@Target({ElementType.ANNOTATION_TYPE, ElementType.FIELD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Test5 {

    @MyAliasFor("test2")
    @MyAliasFor("test3")
    String test1() default "test1";

    @MyAliasFor("test1")
    @MyAliasFor("test3")
    String test2() default "test1";

    @MyAliasFor("test1")
    @MyAliasFor("test2")
    String test3() default "test1";
}

@Target({ElementType.ANNOTATION_TYPE, ElementType.FIELD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Test5
public @interface Test6 {

    @MyAliasFor("test2")
    @MyAliasFor("test3")
    String test1() default "test2";

    @MyAliasFor("test1")
    @MyAliasFor("test3")
    String test2() default "test2";

    @MyAliasFor(annotation = Test5.class)
    @MyAliasFor("test1")
    @MyAliasFor("test2")
    String test3() default "test2";
}

@Target({ElementType.ANNOTATION_TYPE, ElementType.FIELD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Test6
public @interface Test7 {

    @MyAliasFor(annotation = Test6.class)
    String test3() default "test3";
}

@Test7(test3 = "override the method")
public static class Element4 {
}

@Test
public void test3() {
    Test5 test5 = MyAnnotatedElementUtils.getMergedAnnotation(Element4.class, Test5.class);
    Test6 test6 = MyAnnotatedElementUtils.getMergedAnnotation(Element4.class, Test6.class);
    System.out.println(test5.toString());
    System.out.println(test6.toString());
    assertEquals("override the method", test6.test1());
    assertEquals("override the method", test6.test2());
    assertEquals("override the method", test6.test3());
    assertEquals("override the method", test5.test1());
    assertEquals("override the method", test5.test2());
    assertEquals("override the method", test5.test3());
}

/**
 * fixme 特别注意！！！这个测试用例是不通过的！！！
 */
@Test
public void test4() {
    Test8 test8 = MyAnnotatedElementUtils.getMergedAnnotation(Element5.class, Test8.class);
    Test9 test9 = MyAnnotatedElementUtils.getMergedAnnotation(Element5.class, Test9.class);
    System.out.println(test8.toString());
    System.out.println(test9.toString());
    assertEquals("override the method", test9.test1());
    assertEquals("override the method", test9.test2());
    assertEquals("override the method", test9.test3());
    assertEquals("override the method", test8.test1());
    assertEquals("override the method", test8.test2());
    assertEquals("override the method", test8.test3());
}
```

有关更多详细信息，请阅读ʻorg.springframework.core.annotation.AlisforsTests`。
