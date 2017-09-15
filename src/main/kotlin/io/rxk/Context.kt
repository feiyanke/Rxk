package io.rxk

import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

class Context<T, R> (
        var signal: IMethod<T, R>,
        var error: IEasyMethod<Throwable>,
        var finish: IUnitMethod,

        var start: IUnitMethod,
        var cancel: IUnitMethod,
        var report: IUnitMethod
) {

    fun filter(predicate:(R)->Boolean):Context<T, R> = make(FilterOperator(predicate))
    fun distinct():Context<T, R> = make(DistinctOperator())
    fun <E> map(tranform:(R)->E):Context<T, E> = make(MapOperator(tranform))
    fun <E> map(method:IMethod<R, E>):Context<T, E> = make(method)
    fun <E> mapCallback(callback:(R, (E)->Unit)->Unit):Context<T, E> = make(MapCallbackOperator(callback))
    fun <E> mapFuture(method:(R)->Future<E>):Context<T, E> = make(MapFutureOperator(method))
    fun scan(init:R, method:(R,R)->R):Context<T, R> = make(ScanOperator(init, method))
    fun multiScan(vararg init:R, m:(List<R>,R)->R):Context<T, R> = make(MultiScanOperator(*init, method = m))
    fun forEach(report:Boolean = true, block:(R)->Unit):Context<T, R> = make(ForEachOperator(report, block))
    fun finish(block: () -> Unit):Context<T, R> = make(FinishOperator(block))
    fun error(block: (e:Throwable) -> Unit):Context<T, R> = make(ErrorOperator(block))
    fun take(n:Int):Context<T, R> = make(TakeOperator(n))
    fun on(executor: Executor):Context<T, R> = make(ScheduleOperator(executor))
    fun log(block: (R) -> String):Context<T, R> = make(LogOperator(block))
    fun parallel():Context<T, R> = on(Executors.newCachedThreadPool())
    fun pack(n:Int):Context<T, R> = make(PackOperator(n))
    fun buffer(count:Int) = make(BufferOperator(count))
    fun <E> flatMap(transform:(R)->Context<*, E>):Context<T, E> = make(FlatMapOperator(transform))
    fun elementAt(index:Int):Context<T, R> = make(ElementAtOperator(index))
    fun first():Context<T, R> = elementAt(0)
    fun last():Context<T, R> = make(LastOperator())
    fun skip(count: Int):Context<T, R> = make(SkipOperator(count))

    companion object {
        fun <T> create(block:Stream<T>.()->Unit):Context<T, T> = make(BlockStream(block))
        fun fromRunable(block:()->Unit):Context<Unit, Unit> = make(RunableStream(block))
        fun from(runnable: Runnable):Context<Unit, Unit> = fromRunable(runnable::run)
        fun <T> fromCallable(callable:()->T):Context<T, T> = make(CallableStream(callable))
        fun <T> from(callable: Callable<T>):Context<T, T> = fromCallable(callable::call)
        fun <T> from(future: Future<T>):Context<T, T> = fromCallable(future::get)
        fun <T> from(iterable: Iterable<T>):Context<T, T> = make(IterableStream(iterable))
        fun <T> from(array: Array<T>):Context<T, T> = make(IterableStream(array.asIterable()))
        fun <T> just(vararg values:T):Context<T, T> = from(values.asIterable())
        fun range(n:Int, m:Int):Context<Int, Int> = from(n until m)
        fun interval(ms: Long):Context<Int, Int> = make(IntervalStream(ms))

        private fun <T> make(o: Stream<T>):Context<T, T> = o.make()
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

    private fun <E> chainNext(m:IMethod<R, E>) : Context<T, E> = Context(signal.chain(m), error, finish, start, cancel, report)
    private fun chainNext(m:IEasyMethod<R>?) = m?.let { signal = signal.chain(m) }
    private fun chainError(m:IEasyMethod<Throwable>?) = m?.let { error = error.chain(m) }
    private fun chainFinish(m:IUnitMethod?) = m?.let { finish = finish.chain(m) }
    private fun chainStart(m:IUnitMethod?) = m?.let { start = it.chain(start) }
    private fun chainCancel(m:IUnitMethod?) = m?.let { cancel = it.chain(cancel) }
    private fun chainReport(m:IUnitMethod?) = m?.let { report = it.chain(report) }
}

fun testMap(n:Int) : String {
    Thread.sleep(1000)
    return n.toString()
}

fun testMapAsync(n:Any, cb:(String)->Unit){
    thread {
        Thread.sleep(1000)
        cb(n.toString())
    }
}

fun main(args: Array<String>) {
    var count = AtomicInteger(0)

    Context.just(0,1,1,2,1,3,4,0,3)
            .pack(1)
//            .parallel()
//            .flatMap { (0..it).asStream() }
//            .pack(1)
            //.pack(1)
            //.buffer(4)
            //.pack(2)
            //.on(Executors.newCachedThreadPool())
            //.take(30)
            //.multiScan(0,0){a,b->a.sum()+b}
            //.parallel()
            //.pack(5)
            //.pack(7)
            //.parallel()
            //.pack(10)
            //.parallel()
            //.filter{it<15}
            //.distinct()
            //.pack(2)
            .skip(3)
            .log { "start:$it:thread:${Thread.currentThread()}" }
            .mapCallback(::testMapAsync)
            .log { "end:$it" }
            .forEach { println("count:${count.incrementAndGet()}") }
            .finish{ println("finish:$count") }
            .start()
}