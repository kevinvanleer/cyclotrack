package com.kvl.cyclotrack

import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy

@Retention(RetentionPolicy.RUNTIME)
@Target(AnnotationTarget.FUNCTION,
    AnnotationTarget.ANNOTATION_CLASS)
annotation class Repeat(val value: Int = 1)