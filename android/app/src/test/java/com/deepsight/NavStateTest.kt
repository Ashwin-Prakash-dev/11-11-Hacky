package com.deepsight

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The bottom bar's tabs: each keeps its own back stack, and back ends on Single's first screen. */
class NavStateTest {
    private val start = NavState()

    @Test
    fun startsOnSingleWithEveryTabAtItsRoot() {
        assertEquals(Tab.SINGLE, start.tab)
        assertEquals(Route.Home, start.route)
        assertEquals(Route.Batch, start.top(Tab.BATCH))
        assertEquals(Route.Profile, start.top(Tab.PROFILE))
    }

    @Test
    fun openPushesOntoTheOpenTabOnly() {
        val nav = start.open(Route.History)
        assertEquals(listOf(Route.Home, Route.History), nav.stack)
        assertEquals(listOf(Route.Profile), nav.stacks.getValue(Tab.PROFILE))
    }

    @Test
    fun switchingTabsKeepsEachTabsStack() {
        val nav = start.open(Route.History).select(Tab.PROFILE).open(Route.About).select(Tab.SINGLE)
        assertEquals(Route.History, nav.route)
        assertEquals(Route.About, nav.select(Tab.PROFILE).route)
    }

    @Test
    fun reselectingTheOpenTabGoesToItsFirstScreen() {
        val nav = start.open(Route.Case).open(Route.Result).select(Tab.SINGLE)
        assertEquals(listOf(Route.Home), nav.stack)
    }

    @Test
    fun backPopsWithinTheTabThenReturnsToSingleThenCloses() {
        val nav = start.select(Tab.PROFILE).open(Route.About)
        val profileRoot = nav.back()!!
        assertEquals(Tab.PROFILE, profileRoot.tab)
        assertEquals(Route.Profile, profileRoot.route)

        val single = profileRoot.back()!!
        assertEquals(Tab.SINGLE, single.tab)
        assertEquals(Route.Home, single.route)
        assertEquals(listOf(Route.Profile), single.stacks.getValue(Tab.PROFILE))

        assertNull(single.back())
    }

    @Test
    fun openOnAHiddenTabLeavesTheVisibleTabAlone() {
        // An analysis finishing while the user is on Profile puts its result on Single, without switching tabs.
        val nav = start.open(Route.Case).select(Tab.PROFILE).open(Route.Result, Tab.SINGLE)
        assertEquals(Tab.PROFILE, nav.tab)
        assertEquals(listOf(Route.Home, Route.Case, Route.Result), nav.stacks.getValue(Tab.SINGLE))
    }

    @Test
    fun resetReplacesATabsStackAndShowsIt() {
        val nav = start.open(Route.Case).open(Route.Result).select(Tab.BATCH).reset(Tab.SINGLE, listOf(Route.Home, Route.History))
        assertEquals(Tab.SINGLE, nav.tab)
        assertEquals(listOf(Route.Home, Route.History), nav.stack)
    }

    @Test
    fun dropTopRemovesOnlyTheNamedRoute() {
        val nav = start.open(Route.Case).open(Route.Result)
        assertEquals(listOf(Route.Home, Route.Case), nav.dropTop(Tab.SINGLE, Route.Result).stack)
        assertEquals(nav, nav.dropTop(Tab.SINGLE, Route.History))
    }
}
