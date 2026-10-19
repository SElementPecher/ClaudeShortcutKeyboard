package io.github.selementpecher.shortcutkeyboard.ime

/** Paging for the tile grid: always [PER_PAGE] slots per page. Plain Kotlin, no Android types. */
object GridPaging {
    const val PER_PAGE = 6

    /** How many pages [itemCount] tiles need. There is always at least one page. */
    fun pageCount(itemCount: Int): Int = maxOf(1, (itemCount + PER_PAGE - 1) / PER_PAGE)

    fun clampPage(page: Int, itemCount: Int): Int = page.coerceIn(0, pageCount(itemCount) - 1)

    /** Position in the whole list of the tile in [slot] (0..5) on [page]. */
    fun globalIndex(page: Int, slot: Int): Int = page * PER_PAGE + slot

    fun pageOf(globalIndex: Int): Int = globalIndex / PER_PAGE

    fun slotOf(globalIndex: Int): Int = globalIndex % PER_PAGE

    /** The tiles shown on [page]. */
    fun <T> pageItems(items: List<T>, page: Int): List<T> {
        val from = (page * PER_PAGE).coerceAtMost(items.size)
        return items.subList(from, (from + PER_PAGE).coerceAtMost(items.size))
    }

    fun hasPrevious(page: Int): Boolean = page > 0

    fun hasNext(page: Int, itemCount: Int): Boolean = page < pageCount(itemCount) - 1
}
