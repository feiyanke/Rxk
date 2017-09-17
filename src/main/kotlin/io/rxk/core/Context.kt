package io.rxk.core

import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

class Context<T, R> (
        var signal: IMethod<T, R>,
        var error: IEasyMethod<Throwable>,
        var finish: IUnitMethod,

        var start: IUnitMethod,
        var cancel: IUnitMethod,
        var report: IUnitMethod
) {

    fun filter(predicate:(R)->Boolean): Context<T, R> = make(FilterOperator(predicate))
    fun distinct(): Context<T, R> = make(DistinctOperator())
    fun <E> map(tranform:(R)->E): Context<T, E> = make(MapOperator(tranform))
    fun <E> map(method: IMethod<R, E>): Context<T, E> = make(method)
    fun <E> mapCallback(callback:(R, (E)->Unit)->Unit): Context<T, E> = make(MapCallbackOperator(callback))
    fun <E> mapFuture(method:(R)->Future<E>): Context<T, E> = make(MapFutureOperator(method))
    fun scan(init:R? = null, method:(R,R)->R): Context<T, R> = make(ScanOperator(init, method))
    fun reduce(init:R? = null, method:(R,R)->R) = scan(init, method).last()?:init
    fun multiScan(vararg init:R, m:(List<R>,R)->R): Context<T, R> = make(MultiScanOperator(*init, method = m))
    fun forEach(block:(R)->Unit): Context<T, R> = make(ForEachOperator(block))
    fun error(block: (e:Throwable) -> Unit): Context<T, R> = make(ErrorOperator(block))
    fun take(n:Int): Context<T, R> = make(TakeOperator(n))
    fun takeLast(n:Int): Context<T, R> = make(TakeLastOperator(n))
    fun log(block: (R) -> String): Context<T, R> = make(LogOperator(block))
    fun print() = forEach(::println).finish()
    fun on(executor: Executor): Context<T, R> = make(ScheduleOperator(executor))
    fun parallel(): Context<T, R> = on(Executors.newCachedThreadPool())
    fun pack(n:Int): Context<T, R> = make(PackOperator(n))
    fun serialze(): Context<T, R> = pack(1)
    fun buffer(count:Int) = make(BufferOperator(count))
    fun <E> flatMap(transform:(R)-> Context<*, E>): Context<T, E> = make(FlatMapOperator(transform))
    fun elementAt(index:Int): Context<T, R> = make(ElementAtOperator(index))
    fun first(): Context<T, R> = elementAt(0)
    fun skip(count: Int): Context<T, R> = make(SkipOperator(count))
    fun skipLast(count: Int): Context<T, R> = make(SkipLastOperator(count))
    fun startWith(context: Context<*, R>): Context<*, R> = Companion.concat(context, this)
    fun startWith(vararg v:R): Context<*, R> = Companion.concat(just(*v), this)
    fun startWith(list:List<R>): Context<*, R> = Companion.concat(from(list), this)
    fun merge(vararg context: Context<*, R>, sync: Boolean = false): Context<*, R> = Companion.merge(this, *context, sync = sync)
    fun concat(vararg context: Context<*, R>): Context<*, R> = Companion.concat(this, *context)
    fun zip(vararg context: Context<*, R>): Context<*, List<R>> = Companion.zip(this, *context)
    fun timeInterval(): Context<T, Long> = make(TimeIntervalOperator())
    fun timeStamp(): Context<T, TimeStamp<R>> = map { TimeStamp(it) }
    fun indexStamp(): Context<T, IndexStamp<R>> = make(IndexedOperator())
    fun takeUntil(predicate: (R) -> Boolean): Context<T, R> = make(TakeUntilOperator(predicate))
    fun takeWhile(predicate: (R) -> Boolean): Context<T, R> = make(TakeWhileOperator(predicate))
    fun skipUntil(predicate: (R) -> Boolean): Context<T, R> = make(SkipUntilOperator(predicate))
    fun skipWhile(predicate: (R) -> Boolean): Context<T, R> = make(SkipWhileOperator(predicate))
    fun finish(block: () -> Unit) = make(FinishOperator(block)).start()
    fun finish() {
        val latch = CountDownLatch(1)
        finish { latch.countDown() }
        latch.await()
    }
    fun last(block: (R?) -> Unit) = make(LastOperator()).forEach(block).finish{}
    fun last() : R? {
        val latch = ValueLatch<R?>()
        last {
            latch.set(it)
        }
        return latch.get()
    }
    fun all(predicate: (R) -> Boolean) = map(predicate).takeUntil { !it }.last()!!
    fun contains(v:R) = takeUntil { it == v }.last() == v
    fun any(predicate: (R) -> Boolean) = map(predicate).takeUntil { it }.last()!!
    fun count() = (indexStamp().last()?.index?:-1) + 1

    companion object {
        fun <T> create(block: Stream<T>.()->Unit): Context<T, T> = make(BlockStream(block))
        fun fromRunable(block:()->Unit): Context<Unit, Unit> = make(RunableStream(block))
        fun from(runnable: Runnable): Context<Unit, Unit> = fromRunable(runnable::run)
        fun <T> fromCallable(callable:()->T): Context<T, T> = make(CallableStream(callable))
        fun <T> from(callable: Callable<T>): Context<T, T> = fromCallable(callable::call)
        fun <T> from(future: Future<T>): Context<T, T> = fromCallable(future::get)
        fun <T> from(iterable: Iterable<T>): Context<T, T> = make(IterableStream(iterable))
        fun <T> from(array: Array<T>): Context<T, T> = make(IterableStream(array.asIterable()))
        fun <T> just(vararg values:T): Context<T, T> = from(values.asIterable())
        fun range(n:Int, m:Int): Context<Int, Int> = from(n until m)
        fun interval(ms: Long): Context<Int, Int> = make(IntervalStream(ms))
        fun <T> merge(vararg context: Context<*, T>, sync:Boolean = false): Context<*, T> = make(MergeStream(sync, context.asList()))
        fun <T> concat(vararg context: Context<*, T>): Context<*, T> = merge(*context, sync = true)
        fun <T> zip(vararg context: Context<*, T>): Context<*, List<T>> = make(ZipStream(context.asList()))

        private fun <T> make(o: Stream<T>): Context<T, T> = o.make()
    }

    fun <E> make(m: Operator<R, E>) = make(m.signal, m.error, m.finish, m.start, m.cancel, m.report)
    fun make(m: EasyOperator<R>) = make(m.signal, m.error, m.finish, m.start, m.cancel, m.report)

    fun <E> make(next : IMethod<R, E>,
                 error : IEasyMethod<Throwable>? = null,
                 finish : IUnitMethod? = null,
                 start : IUnitMethod? = null,
                 cancel : IUnitMethod? = null,
                 report : IUnitMethod? = null
    ) : Context<T, E> = chainNext(next).apply {
        chainError(error)
        chainFinish(finish)
        chainStart(start)
        chainCancel(cancel)
        chainReport(report)
    }

    fun make(next : IEasyMethod<R>? = null,
             error : IEasyMethod<Throwable>? = null,
             finish : IUnitMethod? = null,
             start : IUnitMethod? = null,
             cancel : IUnitMethod? = null,
             report : IUnitMethod? = null
    ) : Context<T, R> = apply {
        chainNext(next)
        chainError(error)
        chainFinish(finish)
        chainStart(start)
        chainCancel(cancel)
        chainReport(report)
    }

    private fun <E> chainNext(m: IMethod<R, E>) : Context<T, E> = Context(signal.chain(m), error, finish, start, cancel, report)
    private fun chainNext(m: IEasyMethod<R>?) = m?.let { signal = signal.chain(m) }
    private fun chainError(m: IEasyMethod<Throwable>?) = m?.let { error = error.chain(m) }
    private fun chainFinish(m: IUnitMethod?) = m?.let { finish = finish.chain(m) }
    private fun chainStart(m: IUnitMethod?) = m?.let { start = it.chain(start) }
    private fun chainCancel(m: IUnitMethod?) = m?.let { cancel = it.chain(cancel) }
    private fun chainReport(m: IUnitMethod?) = m?.let { report = it.chain(report) }
}

