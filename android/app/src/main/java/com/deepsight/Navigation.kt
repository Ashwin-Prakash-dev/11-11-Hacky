package com.deepsight

/** The bottom bar's tabs, left to right. Each keeps its own back stack; the app starts on [SINGLE]. */
enum class Tab(val root: Route) { BATCH(Route.Batch), SINGLE(Route.Home), PROFILE(Route.Profile) }

/** Which tab is open and every tab's back stack. Pure, so [AppViewModel] keeps it in one StateFlow. */
data class NavState(
    val tab: Tab = Tab.SINGLE,
    val stacks: Map<Tab, List<Route>> = Tab.entries.associateWith { listOf(it.root) },
) {
    val stack: List<Route> get() = stacks.getValue(tab)
    val route: Route get() = stack.last()

    fun top(tab: Tab): Route = stacks.getValue(tab).last()

    /** Tapping the open tab again goes back to its first screen. */
    fun select(tab: Tab): NavState = if (tab == this.tab) reset(tab, listOf(tab.root)) else copy(tab = tab)

    /** Pushes onto [tab], the open one by default; another tab's push doesn't switch to it. */
    fun open(route: Route, tab: Tab = this.tab): NavState = copy(stacks = stacks + (tab to stacks.getValue(tab) + route))

    fun reset(tab: Tab, stack: List<Route>): NavState = copy(tab = tab, stacks = stacks + (tab to stack))

    fun dropTop(tab: Tab, route: Route): NavState =
        if (top(tab) == route && stacks.getValue(tab).size > 1) copy(stacks = stacks + (tab to stacks.getValue(tab).dropLast(1))) else this

    /** Back within the tab, then from another tab's first screen to Single; null on Single's first screen (the app closes). */
    fun back(): NavState? = when {
        stack.size > 1 -> copy(stacks = stacks + (tab to stack.dropLast(1)))
        tab != Tab.SINGLE -> copy(tab = Tab.SINGLE)
        else -> null
    }
}
