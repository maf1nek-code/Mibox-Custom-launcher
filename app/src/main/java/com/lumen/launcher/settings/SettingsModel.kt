package com.lumen.launcher.settings

import com.lumen.launcher.gfx.BackdropSpec

/** One row in a settings list. [desc] is shown in the left pane while the row is focused. */
sealed class Item(val title: String, val desc: String) {
    /** When set, the left pane shows this background instead of the page icon. */
    open val preview: BackdropSpec? get() = null
}

class Nav(title: String, desc: String, val value: (() -> String)? = null, val target: () -> Page) : Item(title, desc)

class Toggle(title: String, desc: String, val get: () -> Boolean, val set: (Boolean) -> Unit) : Item(title, desc)

class Choice(
    title: String, desc: String,
    val options: List<String>,
    val optionDescs: List<String>,
    val get: () -> Int,
    val set: (Int) -> Unit,
) : Item(title, desc)

class Radio(
    title: String, desc: String,
    val selected: () -> Boolean,
    val pick: () -> Unit,
    private val previewSpec: BackdropSpec? = null,
) : Item(title, desc) {
    override val preview: BackdropSpec? get() = previewSpec
}

class Action(title: String, desc: String, val danger: Boolean = false, val run: () -> Unit) : Item(title, desc)

class Info(title: String, desc: String, val value: () -> String = { "" }) : Item(title, desc)

class Movable(title: String, desc: String) : Item(title, desc)

class Page(
    val title: String,
    val icon: Int,
    /** Present on pages whose rows can be picked up and moved. */
    val reorder: ((from: Int, to: Int) -> Unit)? = null,
    /** After a Radio is picked: go back (true) or stay and refresh (false). */
    val popOnPick: Boolean = true,
    val build: () -> List<Item>,
)
