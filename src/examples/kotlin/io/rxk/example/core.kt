package io.rxk.example

import io.rxk.core.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

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
    val aa = emptyList<Int>().asStream().last()
    println("1111111111111111")
//    Context.from(list).startWith(startList).print()


//    Context.just(0,1,1,2,1,3,4,0,3)
//    Context.merge((0..10).asStream(), (20..30).asStream())
//            .zip((40..80).asStream())
    val a = Context.just(0, 1, 1, 2, 1, 3, 4, 0, 3)
            //.timeInterval()
            .parallel()
            .average()

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
//    println("finish:$count")
}