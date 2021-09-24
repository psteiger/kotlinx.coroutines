/*
 * Copyright 2016-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.coroutines.test

import kotlinx.coroutines.*
import kotlin.coroutines.*

/**
 * A scope which provides detailed control over the execution of coroutines for tests.
 */
@ExperimentalCoroutinesApi // Since 1.2.1, tentatively till 1.3.0
public interface TestCoroutineScope: CoroutineScope, DelayController {
    /**
     * Is being called after a test completes.
     *
     * Calls [DelayController.cleanupTestCoroutines].
     *
     * If the [CoroutineExceptionHandler] is a [TestCoroutineExceptionHandler], its
     * [TestCoroutineExceptionHandler.cleanupTestCoroutines] behavior is performed:
     * the first exception in [uncaughtExceptions] is rethrown, and all the other exceptions are
     * printed using [Throwable.printStackTrace].
     *
     * @throws Throwable the first uncaught exception, if there are any uncaught exceptions.
     * @throws UncompletedCoroutinesError if any pending tasks are active, however it will not throw for suspended
     * coroutines.
     */
    public override fun cleanupTestCoroutines()

    /**
     * List of uncaught coroutine exceptions.
     *
     * Exceptions are only collected in this list if [TestCoroutineExceptionHandler] is in the test context and this
     * scope's job is a [SupervisorJob].
     * Otherwise, the failure of one child cancels all the others and the scope itself.
     *
     * The returned list is a copy of the exceptions caught during execution.
     * During [cleanupTestCoroutines] the first element of this list is rethrown if it is not empty.
     */
    @Deprecated(
        "This list is only populated if `TestCoroutineExceptionHandler` in the test context, and so can be " +
            "easily misused. It is only present for backward compatibility and will be removed in the subsequent " +
            "releases. If you need to check the list of exceptions, please consider  your own" +
            "`CoroutineExceptionHandler`.",
        level = DeprecationLevel.WARNING)
    public val uncaughtExceptions: List<Throwable>
}

private class TestCoroutineScopeImpl (
    override val coroutineContext: CoroutineContext
):
    TestCoroutineScope,
    DelayController by coroutineContext.delayController
{
    override fun cleanupTestCoroutines() {
        (coroutineContext[CoroutineExceptionHandler] as? TestCoroutineExceptionHandler)?.cleanupTestCoroutinesCaptor()
        coroutineContext.delayController.cleanupTestCoroutines()
    }

    override val uncaughtExceptions: List<Throwable>
        get() = (coroutineContext[CoroutineExceptionHandler] as? TestCoroutineExceptionHandler)?.uncaughtExceptions
            ?: emptyList()
}

/**
 * A scope which provides detailed control over the execution of coroutines for tests.
 *
 * If the provided context does not provide a [ContinuationInterceptor] (Dispatcher) or [CoroutineExceptionHandler], the
 * scope adds [TestCoroutineDispatcher] and [TestCoroutineExceptionHandler] automatically.
 *
 * @param context an optional context that MAY provide [UncaughtExceptionCaptor] and/or [DelayController]
 */
@Suppress("FunctionName")
@ExperimentalCoroutinesApi // Since 1.2.1, tentatively till 1.3.0
public fun TestCoroutineScope(context: CoroutineContext = EmptyCoroutineContext): TestCoroutineScope =
    TestCoroutineScopeImpl(context.checkTestScopeArguments().first)

private inline val CoroutineContext.delayController: DelayController
    get() {
        val handler = this[ContinuationInterceptor]
        return handler as? DelayController ?: throw IllegalArgumentException(
            "TestCoroutineScope requires a DelayController such as TestCoroutineDispatcher as " +
                "the ContinuationInterceptor (Dispatcher)"
        )
    }
