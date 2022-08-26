package com.kvl.cyclotrack

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestCoroutineDispatcher
import kotlinx.coroutines.test.TestCoroutineExceptionHandler
import kotlinx.coroutines.test.createTestCoroutineScope
import kotlin.coroutines.CoroutineContext

//From:
//https://medium.com/@daptronic/kotlin-coroutines-android-how-to-unit-test-lifecyclecoroutinescope-654ab324f3e7

class TestScope(override val coroutineContext: CoroutineContext) : ManagedCoroutineScope {
    val scope =
        createTestCoroutineScope(TestCoroutineDispatcher() + TestCoroutineExceptionHandler() + coroutineContext)

    override fun launch(block: suspend CoroutineScope.() -> Unit): Job {
        return scope.launch {
            block.invoke(this)
        }
    }
}
