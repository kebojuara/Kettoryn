package com.williamchandra.kettoryn

import android.graphics.Bitmap
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Runs only against the clean distribution-verification installation; never resets data. */
class ReleaseContentTest {
    @Test fun aboutCreditsAndOfflineNoticesInBothThemes() {
        val inst = InstrumentationRegistry.getInstrumentation()
        val context = inst.targetContext
        val out = File(context.getExternalFilesDir(null), "release-content").apply { mkdirs() }
        fun settle() { inst.waitForIdleSync(); Thread.sleep(500) }
        fun nodes(): List<AccessibilityNodeInfo> {
            inst.uiAutomation.clearCache()
            val result = mutableListOf<AccessibilityNodeInfo>()
            fun visit(n: AccessibilityNodeInfo) { result.add(n); for(i in 0 until n.childCount) n.getChild(i)?.let(::visit) }
            inst.uiAutomation.rootInActiveWindow?.let(::visit)
            return result
        }
        fun text(label: String) = nodes().firstOrNull { it.text?.toString() == label || it.contentDescription?.toString() == label }
        fun click(label: String) {
            settle()
            var found: AccessibilityNodeInfo? = null
            for (attempt in 0 until 12) {
                found = text(label)
                if (found != null) break
                settle()
            }
            if (found == null) {
                File(out, "missing-node.txt").writeText(nodes().joinToString("\n") { "${it.text} / ${it.contentDescription} / ${it.className}" })
            }
            var n = requireNotNull(found) { "Missing $label" }
            while(!n.isClickable && n.parent != null) n=n.parent
            assertTrue("Click $label", n.performAction(AccessibilityNodeInfo.ACTION_CLICK)); settle()
        }
        fun scrollTo(label: String) {
            repeat(10) {
                if(text(label)!=null) return
                nodes().filter { it.isScrollable }.lastOrNull()?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                settle()
            }
            error("Unreachable $label")
        }
        fun capture(name:String) { settle(); inst.uiAutomation.takeScreenshot().let { bitmap -> File(out,"$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle() } }
        ActivityScenario.launch(MainActivity::class.java).use {
            settle(); click("Settings")
            for(theme in listOf("Dark","Light")) {
                // Return to the top of Settings before selecting the actual persisted theme.
                repeat(10) { nodes().firstOrNull { it.isScrollable }?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD); settle() }
                click(theme)
                scrollTo("About")
                assertTrue(nodes().any { it.text?.toString()?.startsWith("Kettoryn is an offline workout tracker") == true })
                scrollTo("Illustration credits")
                capture("about-${theme.lowercase()}")
                click("Illustration credits")
                assertNotNull(text("Workout Guide")); capture("credits-${theme.lowercase()}-top")
                scrollTo("Full legal notices (offline)");capture("credits-${theme.lowercase()}-bottom")
                click("Full legal notices (offline)")
                assertNotNull(text("Legal notices"));capture("legal-${theme.lowercase()}")
                click("Back to credits");assertNotNull(text("Illustration credits"));click("Close")
            }
        }
        File(out,"result.txt").writeText("PASS actual MainActivity Settings/About/Credits/offline legal, Dark+Light; no data reset")
    }
}
