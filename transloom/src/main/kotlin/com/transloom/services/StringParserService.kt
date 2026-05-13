package com.transloom.services

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

    // Thread-safe via ThreadLocal — DocumentBuilderFactory and TransformerFactory
    // are NOT guaranteed thread-safe per Java docs. With the pipeline processing
    // languages in parallel, concurrent access from coroutines on different threads
    // could cause race conditions. ThreadLocal gives each thread its own cached factory.
    private val builderFactory = ThreadLocal.withInitial { DocumentBuilderFactory.newInstance() }
    private val transformerFactory = ThreadLocal.withInitial { TransformerFactory.newInstance() }

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

            // Fix 16: Parse <string-array> elements — keys are stored as "arrayName[0]", "arrayName[1]" etc.
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
                        element.textContent = translations[key]
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
                                itemElement.textContent = translations[fullKey]
                            }
                        }
                    }
                }
            }

            // Fix 16: Track existing string-array elements for update/append
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
                        if (translations.containsKey(fullKey)) {
                            (items.item(j) as? Element)?.textContent = translations[fullKey]
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
