package com.example.indicT

object Transliterator {

    private val olChikiVowelsMatra = mapOf(
        'ᱚ' to "ो", 'ᱟ' to "ा", 'ᱤ' to "ि", 'ᱩ' to "ु", 'ᱮ' to "े", 'ᱳ' to "ो"
    )

    private val olChikiVowelsIndependent = mapOf(
        'ᱚ' to "अ", 'ᱟ' to "आ", 'ᱤ' to "इ", 'ᱩ' to "उ", 'ᱮ' to "ए", 'ᱳ' to "ओ"
    )

    private val olChikiConsonants = mapOf(
        'ᱛ' to "त", 'ᱜ' to "ग", 'ᱝ' to "ङ", 'ᱞ' to "ल", 'ᱠ' to "क", 'ᱡ' to "ज", 'ᱢ' to "म",
        'ᱣ' to "व", 'ᱥ' to "स", 'ᱦ' to "ह", 'ᱧ' to "ञ", 'ᱨ' to "र", 'ᱪ' to "च", 'ᱫ' to "द",
        'ᱬ' to "ण", 'ᱭ' to "य", 'ᱯ' to "प", 'ᱰ' to "ड", 'ᱱ' to "न", 'ᱲ' to "ड़", 'ᱴ' to "ट", 'ᱵ' to "ब"
    )

    private val aspirateMap = mapOf(
        'ᱠ' to "ख", 'ᱜ' to "घ", 'ᱪ' to "छ", 'ᱡ' to "झ", 'ᱴ' to "ठ", 'ᱰ' to "ढ", 'ᱛ' to "थ", 'ᱫ' to "ध", 'ᱯ' to "फ", 'ᱵ' to "भ"
    )

    private val checkedConsonants = setOf('ᱛ', 'ᱜ', 'ᱪ', 'ᱡ', 'ᱠ', 'ᱫ', 'ᱯ', 'ᱵ', 'ᱧ', 'ᱢ', 'ᱱ', 'ᱨ', 'ᱣ')

    fun olChikiToDevanagari(input: String): String {
        val sb = StringBuilder()
        var i = 0
        val len = input.length

        while (i < len) {
            val c = input[i]

            // 1. Handle short vowels with Gahla Tttu (ᱹ) - e.g. ᱟᱹ (ऑ / ॉ), ᱚᱹ (ऑ / ॉ), ᱮᱹ (ॲ / ॅ)
            if (i + 1 < len && input[i + 1] == 'ᱹ') {
                val hasNasal = (i + 2 < len && (input[i + 2] == 'ᱸ' || input[i + 2] == 'ᱺ'))
                val nasalSuffix = if (hasNasal) "ं" else ""
                val skipCount = if (hasNasal) 3 else 2

                val isMatra = sb.isNotEmpty() && (olChikiConsonants.containsValue(sb.last().toString()) || aspirateMap.containsValue(sb.last().toString()))

                when (c) {
                    'ᱟ', 'ᱚ' -> {
                        sb.append(if (isMatra) "ॉ" else "ऑ").append(nasalSuffix)
                        i += skipCount
                        continue
                    }
                    'ᱮ' -> {
                        sb.append(if (isMatra) "ॅ" else "ॲ").append(nasalSuffix)
                        i += skipCount
                        continue
                    }
                }
            }

            // 2. Check Aspirates (consonant + ᱷ)
            if (i + 1 < len && input[i + 1] == 'ᱷ' && aspirateMap.containsKey(c)) {
                val baseCons = aspirateMap[c]!!
                i += 2
                if (i < len && olChikiVowelsMatra.containsKey(input[i])) {
                    sb.append(baseCons).append(olChikiVowelsMatra[input[i]])
                    i++
                } else {
                    val nextIsCons = i < len && olChikiConsonants.containsKey(input[i])
                    if (nextIsCons) {
                        sb.append(baseCons).append("्")
                    } else {
                        sb.append(baseCons)
                    }
                }
                continue
            }

            // 2b. Special handling for post-vowel word-final ᱣ (OW) -> 'ओ' sound in Santali phonetics (e.g. ᱠᱚᱨᱟᱣ -> कोराओ, ᱥᱟᱨᱦᱟᱣ -> सारहाओ)
            if (c == 'ᱣ') {
                val atBoundary = (i + 1 >= len || isWordBoundary(input[i + 1]))
                val prevChar = if (i > 0) input[i - 1] else ' '
                val prevIsVowel = olChikiVowelsMatra.containsKey(prevChar) || olChikiVowelsIndependent.containsKey(prevChar)
                if (atBoundary && prevIsVowel) {
                    sb.append("ओ")
                    i++
                    continue
                }
            }

            // 3. Consonants & Clusters
            if (olChikiConsonants.containsKey(c)) {
                val cons = olChikiConsonants[c]!!
                i++
                if (i < len && olChikiVowelsMatra.containsKey(input[i])) {
                    sb.append(cons).append(olChikiVowelsMatra[input[i]])
                    i++
                } else {
                    // Internal consonant clusters receive Virama/Halant, but word-final consonants do NOT
                    // so Hindi TTS (eSpeak/Piper) pronounces words naturally without garbling
                    val nextIsConsonant = i < len && olChikiConsonants.containsKey(input[i])
                    if (nextIsConsonant) {
                        sb.append(cons).append("्")
                    } else {
                        sb.append(cons)
                    }
                }
                continue
            }

            // 4. Independent Vowels
            if (olChikiVowelsIndependent.containsKey(c)) {
                sb.append(olChikiVowelsIndependent[c])
            }
            // 5. Nasal Modifiers & Punctuation
            else if (c == 'ᱸ' || c == 'ᱺ') {
                sb.append("ं")
            } else if (c == 'ᱶ') {
                sb.append("ँ")
            } else if (c == 'ᱹ' || c == 'ᱼ') {
                // Ignore raw tone marks
            } else if (c == 'ᱽ') {
                sb.append("्")
            } else if (c == '᱾') {
                sb.append("।")
            } else if (c == '᱿') {
                sb.append("॥")
            } else {
                sb.append(c)
            }
            i++
        }
        return sb.toString()
    }

    private fun isWordBoundary(ch: Char): Boolean {
        return ch.isWhitespace() || ch == ',' || ch == '.' || ch == '!' || ch == '?' || ch == '᱾' || ch == '᱿'
    }
}
