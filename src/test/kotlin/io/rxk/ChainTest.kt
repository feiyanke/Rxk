package io.rxk.chain

import io.rxk.KotlinTests
import org.junit.Test
import org.mockito.Mockito

class ChainTest : KotlinTests() {

    @Test
    fun testCreate() {

        val chain = Map<Int, String> {(it*it).toString()}
                .chain(Map<String, Int> {it.length})
                .chain(Filter {it<3})
                .out { a.received(it) }
        chain(1)
        chain(4)
        chain(100)
        Mockito.verify(a, Mockito.times(1)).received(1)
        Mockito.verify(a, Mockito.times(1)).received(2)
        Mockito.verify(a, Mockito.times(0)).received(5)
    }

    @Test
    fun test1() {

    }
}