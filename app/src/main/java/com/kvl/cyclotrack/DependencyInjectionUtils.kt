package com.kvl.cyclotrack

import androidx.lifecycle.LifecycleCoroutineScope
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext

//From:
//https://medium.com/@daptronic/kotlin-coroutines-android-how-to-unit-test-lifecyclecoroutinescope-654ab324f3e7

interface ManagedCoroutineScope : CoroutineScope {
    abstract fun launch(block: suspend CoroutineScope.() -> Unit): Job
}

class LifecycleManagedCoroutineScope(
    private val lifecycleCoroutineScope: LifecycleCoroutineScope,
    override val coroutineContext: CoroutineContext,
) : ManagedCoroutineScope {
    override fun launch(block: suspend CoroutineScope.() -> Unit): Job =
        lifecycleCoroutineScope.launchWhenStarted(block)
}

class ViewModelManagedCoroutineScope @Inject constructor(
    private val viewModelCoroutineScope: ViewModelManagedCoroutineScope,
    override val coroutineContext: CoroutineContext,
) : ManagedCoroutineScope {
    override fun launch(block: suspend CoroutineScope.() -> Unit): Job =
        viewModelCoroutineScope.launch(block)
}

fun ViewModel.getViewModelScope(coroutineScope: CoroutineScope?) =
    coroutineScope ?: this.viewModelScope

@Module
@InstallIn(SingletonComponent::class)
class ViewModelModule {
    @Provides
    fun provideCoroutineScope(): CoroutineScope? = null
}

