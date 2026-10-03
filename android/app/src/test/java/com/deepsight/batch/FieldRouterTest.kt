package com.deepsight.batch

import java.io.File
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FieldRouterTest {
    private val packs = listOf("malaria_thin", "breast_breakhis")

    @Test
    fun placeholderOnlyEverPicksFromTheOfferedModules() {
        val router = RandomFieldRouter(Random(42))
        val picks = (1..200).map { router.route(File("img$it.jpg"), packs) }
        assertTrue(picks.all { it in packs })
        assertEquals("both modules get used", packs.toSet(), picks.toSet())
    }

    @Test
    fun placeholderIsDeterministicForASeed() {
        val a = RandomFieldRouter(Random(7))
        val b = RandomFieldRouter(Random(7))
        assertEquals((1..20).map { a.route(File("x"), packs) }, (1..20).map { b.route(File("x"), packs) })
    }

    @Test
    fun noModulesMeansNoAllocation() {
        assertNull(RandomFieldRouter(Random(1)).route(File("x.jpg"), emptyList()))
    }

    @Test
    fun aRealRouterCanSayItDoesNotRecogniseTheImage() {
        val router = FieldRouter { _, _ -> null }
        assertNull(router.route(File("bike.jpg"), packs))
    }
}
