package io.rxk

import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

class Context<T, R:Any> (
        var signal: IMethod<T, R>,
        var error: IEasyMethod<Throwable>,
        var finish: IUnitMethod,

        var start: IUnitMethod,
        var cancel: IUnitMethod,
        var report: IUnitMethod
) {

    fun filter(predicate:(R)->Boolean):Context<T, R> = make(FilterOperator(predicate))
    fun distinct():Context<T, R> = make(DistinctOperator())
    fun <E:Any> map(tranform:(R)->E):Context<T, E> = make(MapOperator(tranform))
    fun <E:Any> map(method:IMethod<R, E>):Context<T, E> = make(method)
    fun <E:Any> mapCallback(callback:(R, (E)->Unit)->Unit):Context<T, E> = make(MapCallbackOperator(callback))
    fun <E:Any> mapFuture(method:(R)->Future<E>):Context<T, E> = make(MapFutureOperator(method))
    fun scan(init:R, method:(R,R)->R):Context<T, R> = make(ScanOperator(init, method))
    fun multiScan(vararg init:R, m:(List<R>,R)->R):Context<T, R> = make(MultiScanOperator(*init, method = m))
    fun forEach(block:(R)->Unit):Context<T, R> = make(ForEachOperator(block))
    fun error(block: (e:Throwable) -> Unit):Context<T, R> = make(ErrorOperator(block))
    fun take(n:Int):Context<T, R> = make(TakeOperator(n))
    fun takeLast(n:Int):Context<T, R> = make(TakeLastOperator(n))
    fun on(executor: Executor):Context<T, R> = make(ScheduleOperator(executor))
    fun log(block: (R) -> String):Context<T, R> = make(LogOperator(block))
    fun parallel():Context<T, R> = on(Executors.newCachedThreadPool())
    fun pack(n:Int):Context<T, R> = make(PackOperator(n))
    fun buffer(count:Int) = make(BufferOperator(count))
    fun <E:Any> flatMap(transform:(R)->Context<*, E>):Context<T, E> = make(FlatMapOperator(transform))
    fun elementAt(index:Int):Context<T, R> = make(ElementAtOperator(index))
    fun first():Context<T, R> = elementAt(0)
    fun skip(count: Int):Context<T, R> = make(SkipOperator(count))
    fun skipLast(count: Int):Context<T, R> = make(SkipLastOperator(count))
    fun startWith(context: Context<*, R>):Context<*, R> = merge(context, this)
    fun merge(vararg context: Context<*, R>, sync: Boolean = true):Context<*, R> = Context.merge(this, *context, sync = sync)
    fun zip(vararg context: Context<*, R>):Context<*, List<R>> = Companion.zip(this, *context)
    fun timeInterval():Context<T, Long> = make(TimeIntervalOperator())
    fun timeStamp():Context<T, TimeStamp<R>> = map { TimeStamp(it) }
    fun indexStamp():Context<T, IndexStamp<R>> = make(IndexedOperator())
    fun until(predicate: (R) -> Boolean):Context<T, R> = make(UntilOperator(predicate))
    fun finish(block: () -> Unit) = make(FinishOperator(block)).start()
    fun finish() {
        val latch = CountDownLatch(1)
        finish { latch.countDown() }
        latch.await()
    }
    fun last(block: (R) -> Unit) = make(LastOperator()).forEach(block).finish{}
    fun last() : R {
        val latch = ValueLatch<R>()
        last { latch.set(it) }
        return latch.get()
    }
//    fun timeout(ms: Long, sync: Boolean = true):Context<T, R> = make(TimeoutOperator(ms, sync))
//    fun all(predicate: (R) -> Boolean, cb:(Boolean)->Unit) {
//        forEach {
//            if (!predicate(it)) {
//                cb(false)
//                cancel()
//            }
//        }.finish {
//            cb(true)
//        }.start()
//    }


    companion object {
        fun <T:Any> create(block:Stream<T>.()->Unit):Context<T, T> = make(BlockStream(block))
        fun fromRunable(block:()->Unit):Context<Unit, Unit> = make(RunableStream(block))
        fun from(runnable: Runnable):Context<Unit, Unit> = fromRunable(runnable::run)
        fun <T:Any> fromCallable(callable:()->T):Context<T, T> = make(CallableStream(callable))
        fun <T:Any> from(callable: Callable<T>):Context<T, T> = fromCallable(callable::call)
        fun <T:Any> from(future: Future<T>):Context<T, T> = fromCallable(future::get)
        fun <T:Any> from(iterable: Iterable<T>):Context<T, T> = make(IterableStream(iterable))
        fun <T:Any> from(array: Array<T>):Context<T, T> = make(IterableStream(array.asIterable()))
        fun <T:Any> just(vararg values:T):Context<T, T> = from(values.asIterable())
        fun range(n:Int, m:Int):Context<Int, Int> = from(n until m)
        fun interval(ms: Long):Context<Int, Int> = make(IntervalStream(ms))
        fun <T:Any> merge(vararg context: Context<*, T>, sync:Boolean = true):Context<*, T> = make(MergeStream(sync, context.asList()))
        fun <T:Any> zip(vararg context: Context<*, T>):Context<*, List<T>> = make(ZipStream(context.asList()))

        private fun <T:Any> make(o: Stream<T>):Context<T, T> = o.make()
    }

    fun <E:Any> make(m: Operator<R, E>) = make(m.signal, m.error, m.finish, m.start, m.cancel, m.report)
    fun make(m: EasyOperator<R>) = make(m.signal, m.error, m.finish, m.start, m.cancel, m.report)

    fun <E:Any> make(next : IMethod<R, E>,
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

    private fun <E:Any> chainNext(m:IMethod<R, E>) : Context<T, E> = Context(signal.chain(m), error, finish, start, cancel, report)
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

//    Context.just(0,1,1,2,1,3,4,0,3)
//    Context.merge((0..10).asStream(), (20..30).asStream())
//            .zip((40..80).asStream())
            val a = Context.interval(500)
                    //.timeInterval()
                    .until { it > 10 }
                    .last()

            println("last : $a")
//                    .timeout(5000)
//            .pack(1)
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
//            .takeLast(2)
//            .log { "start:$it:thread:${Thread.currentThread()}" }
//            .mapCallback(::testMapAsync)
//            .log { "end:$it" }
//            .forEach { count.incrementAndGet() }
//            .error { it.printStackTrace() }
//            .finish()
    println("finish:$count")
}

class TimeStamp<out T>(val value:T) {
    val time = System.currentTimeMillis()
}

class IndexStamp<out T>(val value: T, val index:Int)

class ValueLatch<T:Any> : CountDownLatch(1) {
    lateinit var value:T
    fun set(v:T) {
        value = v
        countDown()
    }
    fun get() : T {
        await()
        return value
    }
    fun get(ms: Long) : T? {
        return if(await(ms, TimeUnit.MILLISECONDS)) {
            value
        } else null
    }
}