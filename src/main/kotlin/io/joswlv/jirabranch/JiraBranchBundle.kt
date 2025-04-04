package io.joswlv.jirabranch

import com.intellij.DynamicBundle
import com.intellij.openapi.diagnostic.Logger
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.MissingResourceException
import java.util.PropertyResourceBundle
import java.util.ResourceBundle

@NonNls
private const val BUNDLE = "messages.JiraBranchBundle"

/**
 * 다국어 지원을 위한 메시지 번들 클래스
 */
object JiraBranchBundle : DynamicBundle(BUNDLE) {
    private val LOG = Logger.getInstance(JiraBranchBundle::class.java)

    private val bundleControl = object : ResourceBundle.Control() {
        override fun newBundle(
            baseName: String,
            locale: Locale,
            format: String,
            loader: ClassLoader,
            reload: Boolean
        ): ResourceBundle? {
            val bundleName = toBundleName(baseName, locale)
            val resourceName = toResourceName(bundleName, "properties")

            return loader.getResourceAsStream(resourceName)?.use { stream ->
                try {
                    val reader = InputStreamReader(stream, StandardCharsets.UTF_8)
                    PropertyResourceBundle(reader)
                } catch (e: Exception) {
                    LOG.error("Failed to load resource bundle $resourceName: ${e.message}")
                    null
                }
            }
        }
    }

    @JvmStatic
    fun message(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any): String {
        try {
            return getMessage(key, *params)
        } catch (e: MissingResourceException) {
            try {
                val bundle = ResourceBundle.getBundle(BUNDLE, Locale.getDefault(), bundleControl)
                val pattern = bundle.getString(key)
                return if (params.isEmpty()) pattern else String.format(pattern, *params)
            } catch (e2: Exception) {
                LOG.warn("Failed to resolve key '$key': ${e2.message}")
                return "[$key]"
            }
        }
    }
}