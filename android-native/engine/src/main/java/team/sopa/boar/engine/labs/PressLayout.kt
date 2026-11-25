package team.sopa.boar.engine.labs

import team.sopa.boar.engine.labs.PressDoc.Block

/**
 * Where Press's pictures sit (adam's mock-up, Nov 13; phase 2): a picture placed CENTER takes the column's width; LEFT or
 * RIGHT takes [SIDE] of it, and the text of the NEXT text block (an answer or a note) flows beside it, then continues at
 * the full width below. A heading never starts beside a picture: after a side picture it starts below it, and so does any
 * block after the one that flowed. The paper on screen and the PDF both follow [plan], with the same widths and the same
 * line rule ([linesBeside]), so the page you see is the page that prints.
 */
object PressLayout {
    /**
     * Keeps a moved block in view (adam, Nov 14: "when i move [a] paragraph to top, can we scroll to it if not visible on
     * screen anymore?"; t9 0143). [blockTop] is the block's top from the viewport's top, all in px. Null when the block is
     * fully visible; else how far to scroll (negative: up): [room] above it, or its bottom [room] above the viewport's
     * bottom when it went down, and a block taller than the viewport lines up its top.
     */
    fun scrollToShow(viewHeight: Float, blockTop: Float, blockHeight: Float, room: Float): Float? {
        if (blockTop >= 0f && blockTop + blockHeight <= viewHeight) return null
        val delta = if (blockTop < 0f || blockHeight + 2 * room > viewHeight) blockTop - room else blockTop + blockHeight + room - viewHeight
        return delta.takeIf { kotlin.math.abs(it) >= 1f }
    }

    /** A side picture's share of the column, and the gap between it and the text. */
    const val SIDE = 0.45f
    const val GUTTER = 14f

    sealed interface Item {
        /** A block on its own, at the full width (a centered picture, or any block not beside one). */
        data class Single(val block: Block) : Item
        /** A side picture, and the text block that flows beside it (null: nothing flows; what follows starts below). */
        data class Beside(val picture: Block.Picture, val text: Block?, val left: Boolean) : Item
    }

    /**
     * Text that may flow beside a picture; a heading (and another picture) never does, nor an empty paragraph, list or
     * note (BOAR-79, Biel's iOS port: beside the picture it was invisible and couldn't be tapped, yet took the side). It
     * goes below the picture at the full width, where its hint shows and a tap edits it.
     */
    fun flows(b: Block?) = when (b) {
        is Block.Answer -> true
        is Block.Note -> b.text.isNotBlank()
        is Block.Paragraph -> b.text.isNotBlank()
        is Block.Bullets -> b.text.isNotBlank()
        else -> false
    }

    fun plan(blocks: List<Block>): List<Item> {
        val out = mutableListOf<Item>()
        var i = 0
        while (i < blocks.size) {
            val b = blocks[i]
            if (b is Block.Picture && b.place != PressDoc.Place.CENTER) {
                val next = blocks.getOrNull(i + 1)?.takeIf(::flows)
                out += Item.Beside(b, next, b.place == PressDoc.Place.LEFT)
                i += if (next != null) 2 else 1
            } else {
                out += Item.Single(b)
                i++
            }
        }
        return out
    }

    /** A side picture's box width in a column of [inner] points. */
    fun sideWidth(inner: Float) = inner * SIDE

    /** The text's width beside a side picture. */
    fun besideWidth(inner: Float) = inner - sideWidth(inner) - GUTTER

    /**
     * How many lines go beside a picture [height] tall: those whose top is above the picture's bottom ([lineTops] from the
     * top of the text beside it, in order). The rest continue at the full width.
     */
    fun linesBeside(lineTops: List<Float>, height: Float): Int = lineTops.takeWhile { it < height }.size
}
