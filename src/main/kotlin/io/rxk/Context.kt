package io.rxk

import java.util.concurrent.*
import kotlin.concurrent.thread

class Context<T, R> (
        var next: IMethod<T, R>,
        var error: IEasyMethod<Throwable>,
        var finish: IUnitMethod,

        var start: IUnitMethod,
        var cancel: IUnitMethod,
        var report: IUnitMethod
) {

    fun filter(predicate:(R)->Boolean) = make(FilterOperator(predicate))
    fun <E> map(tranform:(R)->E) = make(MapOperator(tranform))
    fun <E> map(method:IMethod<R, E>) = make(method)
    fun <E> mapAsync(callback:(R, (E)->Unit)->Unit) = make(MapCallbackOperator(callback))
    fun forEash(block:(R)->Unit) = make(ForEachOperator(block))
    fun finish(block: () -> Unit) = make(FinishOperator(block))
    fun error(block: (e:Throwable) -> Unit) = make(ErrorOperator(block))
    fun take(n:Int) = make(TakeOperator(n))
    fun on(executor: Executor) = make(ScheduleOperator(executor))
    fun log(block: (R) -> String) = make(LogOperator(block))
    fun parallel() = on(Executors.newCachedThreadPool())
    fun pack(n:Int) = make(PackOperator(n))

    companion object {
        fun <S> create(block:Stream<S>.()->Unit) = make(BlockStream(block))
        fun fromRunable(block:()->Unit) = make(RunableStream(block))
        fun from(runnable: Runnable) = fromRunable(runnable::run)
        fun <T> fromCallable(callable:()->T) = make(CallableStream(callable))
        fun <T> from(callable: Callable<T>) = fromCallable(callable::call)
        fun <T> from(future: Future<T>) = fromCallable(future::get)
        fun interval(ms: Long) = make(IntervalStream(ms))

        private fun <T> make(o: Stream<T>) = Context(o.next, o.error, o.finish, o.start, o.cancel, o.report)
    }

    fun <E> make(m: Operator<R, E>) = make(m.next, m.error, m.finish, m.start, m.cancel, m.report)
    fun make(m: EasyOperator<R>) = make(m.next, m.error, m.finish, m.start, m.cancel, m.report)

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

    private fun <E> chainNext(m:IMethod<R, E>) : Context<T, E> = Context(next.chain(m), error, finish, start, cancel, report)
    private fun chainNext(m:IEasyMethod<R>?) = m?.let { next = next.chain(m) }
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

fun main(args: Array<String>) {
    var count = 0
    Context.create<Int> {
        for (i in 0..100) {
            next(i)
        }
        finish() }
            //.on(Executors.newCachedThreadPool())
            //.take(20)
            //.parallel()
            .pack(10)
            .parallel()
            //.filter{it%3==0}
            .log { "start:$it:thread:${Thread.currentThread()}" }
            .map(::testMap)
            .log { "end:$it" }
            .forEash { count++ }
            .finish{ println("finish:$count") }
            .start()
}