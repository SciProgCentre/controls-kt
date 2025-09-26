package center.sciprog.controls.demo.thermo

import org.jetbrains.compose.web.attributes.AttrsScope
import org.w3c.dom.Element

enum class PopoverPlacement{
    TOP, BOTTOM, LEFT, RIGHT
}

fun <T: Element> AttrsScope<T>.popopver(
    title: String,
    content: String,
    placement: PopoverPlacement? = null,
){
    PopoverJs
    popper
    attr("data-bs-toggle", "popover")
    placement?.let { attr("data-bs-placement", it.name.lowercase()) }
    attr("data-bs-title", title)
    attr("data-bs-content", content)
}