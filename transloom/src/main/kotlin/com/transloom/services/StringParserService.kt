package com.transloom.services

import kotlinx.serialization.json.*
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource
import org.slf4j.LoggerFactory
import java.io.StringReader
import java.io.StringWriter
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

object StringParserService {
    private val log = LoggerFactory.getLogger(StringParserService::class.java)

    // Thread-safe via ThreadLocal — DocumentBuilderFactory and TransformerFactory are NOT
    // thread-safe per Java docs. Coalescing disabled so CDATA sections remain as distinct
    // child nodes and can be round-tripped without being converted to escaped text.
    private val builderFactory = ThreadLocal.withInitial {
        DocumentBuilderFactory.newInstance().also { it.isCoalescing = false }
    }
    private val transformerFactory = ThreadLocal.withInitial { TransformerFactory.newInstance() }

    private val jsonFormat = Json { prettyPrint = true; ignoreUnknownKeys = true }

    // Handles escaped quotes in values: "key" = "say \"hello\"";
    private val iosStringRegex = Regex(""""((?:[^"\\]|\\.)*)"\s*=\s*"((?:[^"\\]|\\.)*)";""")

    fun parseAndroidXml(xmlContent: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        if (xmlContent.isBlank()) return result
        try {
            val doc = builderFactory.get().newDocumentBuilder()
                .parse(InputSource(StringReader(xmlContent)))
            doc.documentElement.normalize()
            
            val stringNodes = doc.getElementsByTagName("string")
            for (i in 0 until stringNodes.length) {
                val node = stringNodes.item(i)
                if (node.nodeType == Node.ELEMENT_NODE) {
                    val element = node as Element
                    val key = element.getAttribute("name")
                    // Respect translatable="false" — e.g. <string name="app_name" translatable="false">
                    if (key.isNotEmpty() && element.getAttribute("translatable") != "false") {
                        result[key] = element.textContent
                    }
                }
            }

            val pluralsNodes = doc.getElementsByTagName("plurals")
            for (i in 0 until pluralsNodes.length) {
                val node = pluralsNodes.item(i)
                if (node.nodeType == Node.ELEMENT_NODE) {
                    val element = node as Element
                    val key = element.getAttribute("name")
                    val items = element.getElementsByTagName("item")
                    for (j in 0 until items.length) {
                        val itemNode = items.item(j)
                        if (itemNode.nodeType == Node.ELEMENT_NODE) {
                            val itemElement = itemNode as Element
                            val quantity = itemElement.getAttribute("quantity")
                            if (key.isNotEmpty() && quantity.isNotEmpty()) {
                                result["$key.$quantity"] = itemElement.textContent
                            }
                        }
                    }
                }
            }

            val arrayNodes = doc.getElementsByTagName("string-array")
            for (i in 0 until arrayNodes.length) {
                val node = arrayNodes.item(i)
                if (node.nodeType == Node.ELEMENT_NODE) {
                    val element = node as Element
                    val key = element.getAttribute("name")
                    if (key.isEmpty() || element.getAttribute("translatable") == "false") continue
                    val items = element.getElementsByTagName("item")
                    for (j in 0 until items.length) {
                        val itemNode = items.item(j)
                        if (itemNode.nodeType == Node.ELEMENT_NODE) {
                            result["$key[$j]"] = (itemNode as Element).textContent
                        }
                    }
                }
            }
        } catch (e: Exception) {
            log.error("Failed to parse Android XML: {}", e.message)
        }
        return result
    }

    // Sets an element's text content, preserving a CDATA section if the original used one.
    // Without this, translated strings that originally used <![CDATA[<b>bold</b>]]> would be
    // written back as escaped text (&lt;b&gt;bold&lt;/b&gt;), breaking HTML rendering.
    private fun setElementText(element: Element, value: String) {
        val hasCdata = (0 until element.childNodes.length).any {
            element.childNodes.item(it).nodeType == Node.CDATA_SECTION_NODE
        }
        // Clear all existing child nodes, then append appropriate node type
        while (element.hasChildNodes()) element.removeChild(element.firstChild)
        if (hasCdata) {
            element.appendChild(element.ownerDocument.createCDATASection(value))
        } else {
            element.appendChild(element.ownerDocument.createTextNode(value))
        }
    }

    fun mergeAndroidXml(originalXml: String, translations: Map<String, String>): String {
        if (originalXml.isBlank()) return generateNewAndroidXml(translations)
        try {
            val doc = builderFactory.get().newDocumentBuilder()
                .parse(InputSource(StringReader(originalXml)))
            doc.documentElement.normalize()

            val resourcesNode = doc.getElementsByTagName("resources").item(0) ?: return originalXml

            val stringNodes = doc.getElementsByTagName("string")
            val existingStringKeys = mutableSetOf<String>()
            for (i in 0 until stringNodes.length) {
                val node = stringNodes.item(i)
                if (node.nodeType == Node.ELEMENT_NODE) {
                    val element = node as Element
                    val key = element.getAttribute("name")
                    existingStringKeys.add(key)
                    if (translations.containsKey(key)) {
                        setElementText(element, translations.getValue(key))
                    }
                }
            }

            val pluralsNodes = doc.getElementsByTagName("plurals")
            val existingPluralKeys = mutableSetOf<String>()
            for (i in 0 until pluralsNodes.length) {
                val node = pluralsNodes.item(i)
                if (node.nodeType == Node.ELEMENT_NODE) {
                    val element = node as Element
                    val key = element.getAttribute("name")
                    val items = element.getElementsByTagName("item")
                    for (j in 0 until items.length) {
                        val itemNode = items.item(j)
                        if (itemNode.nodeType == Node.ELEMENT_NODE) {
                            val itemElement = itemNode as Element
                            val quantity = itemElement.getAttribute("quantity")
                            val fullKey = "$key.$quantity"
                            existingPluralKeys.add(fullKey)
                            if (translations.containsKey(fullKey)) {
                                setElementText(itemElement, translations.getValue(fullKey))
                            }
                        }
                    }
                }
            }

            val arrayNodes = doc.getElementsByTagName("string-array")
            val existingArrayItemKeys = mutableSetOf<String>()
            for (i in 0 until arrayNodes.length) {
                val node = arrayNodes.item(i)
                if (node.nodeType == Node.ELEMENT_NODE) {
                    val element = node as Element
                    val key = element.getAttribute("name")
                    val items = element.getElementsByTagName("item")
                    for (j in 0 until items.length) {
                        val fullKey = "$key[$j]"
                        existingArrayItemKeys.add(fullKey)
                        val itemEl = items.item(j) as? Element
                        if (itemEl != null && translations.containsKey(fullKey)) {
                            setElementText(itemEl, translations.getValue(fullKey))
                        }
                    }
                }
            }

            val arrayPattern = Regex("""^(.+)\[(\d+)]$""")
            val newPlurals = mutableMapOf<String, MutableMap<String, String>>()
            val newArrays = mutableMapOf<String, MutableMap<Int, String>>()

            for ((key, value) in translations) {
                val arrayMatch = arrayPattern.matchEntire(key)
                if (arrayMatch != null) {
                    val arrayName = arrayMatch.groupValues[1]
                    val index = arrayMatch.groupValues[2].toInt()
                    if (!existingArrayItemKeys.contains(key)) {
                        newArrays.getOrPut(arrayName) { mutableMapOf() }[index] = value
                    }
                } else if (key.contains(".")) {
                    val parts = key.split(".", limit = 2)
                    if (!existingPluralKeys.contains(key)) {
                        newPlurals.getOrPut(parts[0]) { mutableMapOf() }[parts[1]] = value
                    }
                } else {
                    if (!existingStringKeys.contains(key)) {
                        val newString = doc.createElement("string")
                        newString.setAttribute("name", key)
                        newString.textContent = value
                        resourcesNode.appendChild(newString)
                    }
                }
            }

            for ((pluralName, items) in newPlurals) {
                val newPluralNode = doc.createElement("plurals")
                newPluralNode.setAttribute("name", pluralName)
                for ((qty, v) in items) {
                    val itemNode = doc.createElement("item")
                    itemNode.setAttribute("quantity", qty)
                    itemNode.textContent = v
                    newPluralNode.appendChild(itemNode)
                }
                resourcesNode.appendChild(newPluralNode)
            }

            // Append new string-array elements sorted by index
            for ((arrayName, indexedItems) in newArrays) {
                val newArrayNode = doc.createElement("string-array")
                newArrayNode.setAttribute("name", arrayName)
                indexedItems.entries.sortedBy { it.key }.forEach { (_, v) ->
                    val itemNode = doc.createElement("item")
                    itemNode.textContent = v
                    newArrayNode.appendChild(itemNode)
                }
                resourcesNode.appendChild(newArrayNode)
            }

            return serializeDoc(doc)
        } catch (e: Exception) {
            log.error("Failed to merge Android XML: {}", e.message)
            return originalXml
        }
    }

    private fun generateNewAndroidXml(translations: Map<String, String>): String {
        val doc: Document = builderFactory.get().newDocumentBuilder().newDocument()
        val resources = doc.createElement("resources")
        doc.appendChild(resources)

        val arrayPattern = Regex("""^(.+)\[(\d+)]$""")
        val pluralsMap = mutableMapOf<String, MutableMap<String, String>>()
        val arraysMap = mutableMapOf<String, MutableMap<Int, String>>()

        for ((k, v) in translations) {
            val arrayMatch = arrayPattern.matchEntire(k)
            when {
                arrayMatch != null -> {
                    val name = arrayMatch.groupValues[1]
                    val idx = arrayMatch.groupValues[2].toInt()
                    arraysMap.getOrPut(name) { mutableMapOf() }[idx] = v
                }
                k.contains(".") -> {
                    val parts = k.split(".", limit = 2)
                    pluralsMap.getOrPut(parts[0]) { mutableMapOf() }[parts[1]] = v
                }
                else -> {
                    val elem = doc.createElement("string")
                    elem.setAttribute("name", k)
                    elem.textContent = v
                    resources.appendChild(elem)
                }
            }
        }

        for ((pluralName, items) in pluralsMap) {
            val pluralNode = doc.createElement("plurals")
            pluralNode.setAttribute("name", pluralName)
            for ((qty, v) in items) {
                val itemNode = doc.createElement("item")
                itemNode.setAttribute("quantity", qty)
                itemNode.textContent = v
                pluralNode.appendChild(itemNode)
            }
            resources.appendChild(pluralNode)
        }

        for ((arrayName, indexedItems) in arraysMap) {
            val arrayNode = doc.createElement("string-array")
            arrayNode.setAttribute("name", arrayName)
            indexedItems.entries.sortedBy { it.key }.forEach { (_, v) ->
                val itemNode = doc.createElement("item")
                itemNode.textContent = v
                arrayNode.appendChild(itemNode)
            }
            resources.appendChild(arrayNode)
        }

        return serializeDoc(doc)
    }

    // ── JSON strings (flat or nested, including Flutter ARB) ──────────────────────

    // Parses a flat or nested JSON strings file into a flat key→value map.
    // Nested objects are flattened with "." separator: {"a":{"b":"v"}} → {"a.b": "v"}.
    // ARB metadata keys starting with "@" are skipped.
    // Non-string values (numbers, booleans, arrays, null) are skipped — they aren't translatable.
    fun parseJsonStrings(content: String): Map<String, String> {
        if (content.isBlank()) return emptyMap()
        return try {
            flattenJsonObject(jsonFormat.parseToJsonElement(content).jsonObject)
        } catch (e: Exception) {
            log.error("Failed to parse JSON strings: {}", e.message)
            emptyMap()
        }
    }

    private fun flattenJsonObject(obj: JsonObject, prefix: String = ""): Map<String, String> {
        val result = mutableMapOf<String, String>()
        for ((k, v) in obj) {
            if (k.startsWith("@")) continue  // ARB metadata / description keys
            val key = if (prefix.isEmpty()) k else "$prefix.$k"
            when (v) {
                is JsonObject -> result.putAll(flattenJsonObject(v, key))
                is JsonPrimitive -> if (v.isString) result[key] = v.content
                else -> {}
            }
        }
        return result
    }

    // Merges translations back into the original JSON, preserving structure and key order.
    // Nested JSON is navigated using the same "." flattening used by parseJsonStrings.
    // New keys (not present in the original) are appended at the top level.
    fun mergeJsonStrings(originalContent: String, translations: Map<String, String>): String {
        if (originalContent.isBlank()) {
            val obj = JsonObject(translations.mapValues { JsonPrimitive(it.value) })
            return jsonFormat.encodeToString(JsonObject.serializer(), obj)
        }
        return try {
            val root = jsonFormat.parseToJsonElement(originalContent).jsonObject
            val updated = updateJsonObject(root, translations, "")
            jsonFormat.encodeToString(JsonObject.serializer(), updated)
        } catch (e: Exception) {
            log.error("Failed to merge JSON strings: {}", e.message)
            originalContent
        }
    }

    private fun updateJsonObject(
        obj: JsonObject,
        translations: Map<String, String>,
        prefix: String
    ): JsonObject {
        val result = linkedMapOf<String, JsonElement>()
        for ((k, v) in obj) {
            val key = if (prefix.isEmpty()) k else "$prefix.$k"
            result[k] = when (v) {
                is JsonObject -> updateJsonObject(v, translations, key)
                is JsonPrimitive -> if (v.isString && translations.containsKey(key))
                    JsonPrimitive(translations.getValue(key)) else v
                else -> v
            }
        }
        // Append new flat keys (no ".") that don't exist anywhere in the original
        if (prefix.isEmpty()) {
            for ((key, value) in translations) {
                if (!key.contains(".") && !result.containsKey(key)) {
                    result[key] = JsonPrimitive(value)
                }
            }
        }
        return JsonObject(result)
    }

    private fun serializeDoc(doc: Document): String {
        val transformer = transformerFactory.get().newTransformer()
        transformer.setOutputProperty(OutputKeys.INDENT, "yes")
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4")
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes")
        val writer = StringWriter()
        writer.write("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")
        transformer.transform(DOMSource(doc), StreamResult(writer))
        return writer.toString()
    }

    fun parseIosStrings(content: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        iosStringRegex.findAll(content).forEach { match ->
            result[match.groupValues[1]] = match.groupValues[2]
        }
        return result
    }

    fun mergeIosStrings(originalContent: String, translations: Map<String, String>): String {
        if (originalContent.isBlank()) {
            return translations.entries.joinToString("\n") { "\"${it.key}\" = \"${it.value}\";" }
        }

        var updatedContent = originalContent
        val existingKeys = parseIosStrings(originalContent).keys

        for ((key, value) in translations) {
            if (existingKeys.contains(key)) {
                // Use the same escaped-quote-aware regex for replacement
                val replaceRegex = Regex(""""${Regex.escape(key)}"\s*=\s*"(?:[^"\\]|\\.)*";""")
                updatedContent = updatedContent.replace(replaceRegex, "\"$key\" = \"$value\";")
            }
        }

        val appended = StringBuilder(updatedContent)
        if (!updatedContent.endsWith("\n")) appended.append("\n")
        for ((key, value) in translations) {
            if (!existingKeys.contains(key)) {
                appended.append("\"$key\" = \"$value\";\n")
            }
        }
        return appended.toString()
    }
}
