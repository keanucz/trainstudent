package com.trainstudent.voice

import com.trainstudent.model.ParsedStation
import com.trainstudent.model.StationCandidate
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

/**
 * Fuzzy station matcher with phonetic matching for accent tolerance.
 */
@Singleton
class StationMatcher @Inject constructor() {

    companion object {
        private val PUNCTUATION_REGEX = Regex("""[^\w\s]""")
        private val WHITESPACE_REGEX = Regex("""\s+""")
        
        // UK station database (lazy-initialized, shared across instances)
        private val STATIONS = listOf(
            StationData("Sheffield", "SHF", listOf("sheff")),
            StationData("Manchester Piccadilly", "MAN", listOf("manchester", "piccadilly", "manc")),
            StationData("Manchester Victoria", "MCV", listOf("man vic")),
            StationData("Manchester Oxford Road", "MCO", listOf("oxford road")),
            StationData("Leeds", "LDS", listOf("leeds")),
            StationData("London Euston", "EUS", listOf("euston")),
            StationData("London Kings Cross", "KGX", listOf("kings cross", "kgx")),
            StationData("London St Pancras", "STP", listOf("st pancras", "pancras")),
            StationData("London Paddington", "PAD", listOf("paddington")),
            StationData("London Victoria", "VIC", listOf("victoria")),
            StationData("London Liverpool Street", "LST", listOf("liverpool street")),
            StationData("Liverpool Lime Street", "LIV", listOf("liverpool", "lime street")),
            StationData("Birmingham New Street", "BHM", listOf("birmingham", "brum")),
            StationData("Edinburgh Waverley", "EDB", listOf("edinburgh", "waverley")),
            StationData("Glasgow Central", "GLC", listOf("glasgow")),
            StationData("Glasgow Queen Street", "GLQ", listOf("queen street")),
            StationData("Bristol Temple Meads", "BRI", listOf("bristol")),
            StationData("Newcastle", "NCL", listOf("newcastle")),
            StationData("York", "YRK", listOf("york")),
            StationData("Doncaster", "DON", listOf("doncaster")),
            StationData("Nottingham", "NOT", listOf("nottingham")),
            StationData("Derby", "DBY", listOf("derby")),
            StationData("Leicester", "LEI", listOf("leicester")),
            StationData("Peterborough", "PBO", listOf("peterborough")),
            StationData("Cambridge", "CBG", listOf("cambridge")),
            StationData("Oxford", "OXF", listOf("oxford")),
            StationData("Reading", "RDG", listOf("reading")),
            StationData("Southampton Central", "SOU", listOf("southampton")),
            StationData("Brighton", "BTN", listOf("brighton")),
            StationData("Coventry", "COV", listOf("coventry")),
            StationData("Milton Keynes Central", "MKC", listOf("milton keynes")),
            StationData("Crewe", "CRE", listOf("crewe")),
            StationData("Preston", "PRE", listOf("preston")),
            StationData("Lancaster", "LAN", listOf("lancaster")),
            StationData("Carlisle", "CAR", listOf("carlisle")),
            StationData("Sunderland", "SUN", listOf("sunderland")),
            StationData("Bradford Interchange", "BDI", listOf("bradford")),
            StationData("Huddersfield", "HUD", listOf("huddersfield")),
            StationData("Wakefield Westgate", "WKF", listOf("wakefield")),
            StationData("Stockport", "SPT", listOf("stockport")),
            StationData("Warrington Bank Quay", "WBQ", listOf("warrington")),
            StationData("Chester", "CTR", listOf("chester")),
            StationData("Stoke-on-Trent", "SOT", listOf("stoke")),
            StationData("Wolverhampton", "WVH", listOf("wolverhampton")),
            StationData("Shrewsbury", "SHR", listOf("shrewsbury")),
            StationData("Telford Central", "TFC", listOf("telford")),
            StationData("Newport", "NWP", listOf("newport")),
            StationData("Cardiff Central", "CDF", listOf("cardiff")),
            StationData("Swansea", "SWA", listOf("swansea")),
            StationData("Exeter St Davids", "EXD", listOf("exeter")),
            StationData("Plymouth", "PLY", listOf("plymouth")),
            StationData("Penzance", "PNZ", listOf("penzance")),
            StationData("Norwich", "NRW", listOf("norwich")),
            StationData("Ipswich", "IPS", listOf("ipswich")),
            StationData("Colchester", "COL", listOf("colchester")),
            StationData("Chelmsford", "CHM", listOf("chelmsford")),
            StationData("Gatwick Airport", "GTW", listOf("gatwick")),
            StationData("Heathrow", "HXX", listOf("heathrow")),
            StationData("Stansted Airport", "SSD", listOf("stansted")),
            StationData("Luton Airport Parkway", "LTN", listOf("luton")),
        )
    }

    fun matchStation(input: String): ParsedStation? {
        if (input.isBlank()) return null
        
        val normalizedInput = normalizeInput(input)
        val candidates = STATIONS.mapNotNull { station ->
            val score = calculateMatchScore(normalizedInput, station)
            if (score > 0.5f) StationCandidate(station.name, station.crs, score) else null
        }.sortedByDescending { it.score }

        if (candidates.isEmpty()) return null

        return ParsedStation(
            name = candidates[0].name,
            crs = candidates[0].crs,
            confidence = candidates[0].score,
            alternatives = candidates.drop(1).take(2)
        )
    }

    private fun normalizeInput(input: String): String {
        return input.lowercase()
            .replace(PUNCTUATION_REGEX, "")
            .replace(WHITESPACE_REGEX, " ")
            .trim()
    }

    private fun calculateMatchScore(input: String, station: StationData): Float {
        var maxScore = 0f

        // Check against main name
        maxScore = maxOf(maxScore, fuzzyMatch(input, station.name.lowercase()))

        // Exact CRS match
        if (input.uppercase() == station.crs) return 1.0f

        // Alias matching
        for (alias in station.aliases) {
            maxScore = maxOf(maxScore, fuzzyMatch(input, alias))
        }

        // Phonetic fallback
        if (soundex(input) == soundex(station.name)) {
            maxScore = maxOf(maxScore, 0.85f)
        }

        return maxScore
    }

    private fun fuzzyMatch(s1: String, s2: String): Float = when {
        s1 == s2 -> 1.0f
        s2.contains(s1) || s1.contains(s2) -> 0.9f
        else -> {
            val maxLen = maxOf(s1.length, s2.length)
            if (maxLen == 0) 0f else 1.0f - (levenshteinDistance(s1, s2).toFloat() / maxLen)
        }
    }

    private fun levenshteinDistance(s1: String, s2: String): Int {
        val m = s1.length
        val n = s2.length

        if (m == 0) return n
        if (n == 0) return m

        val dp = Array(m + 1) { IntArray(n + 1) }

        for (i in 0..m) dp[i][0] = i
        for (j in 0..n) dp[0][j] = j

        for (i in 1..m) {
            for (j in 1..n) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = min(
                    min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                    dp[i - 1][j - 1] + cost
                )
            }
        }

        return dp[m][n]
    }

    private fun soundex(input: String): String {
        if (input.isEmpty()) return ""

        val clean = input.uppercase().filter { it.isLetter() }
        if (clean.isEmpty()) return ""

        val result = StringBuilder()
        result.append(clean[0])

        val mapping = mapOf(
            'B' to '1', 'F' to '1', 'P' to '1', 'V' to '1',
            'C' to '2', 'G' to '2', 'J' to '2', 'K' to '2', 'Q' to '2', 'S' to '2', 'X' to '2', 'Z' to '2',
            'D' to '3', 'T' to '3',
            'L' to '4',
            'M' to '5', 'N' to '5',
            'R' to '6'
        )

        var prevCode = mapping[clean[0]] ?: '0'

        for (i in 1 until clean.length) {
            val code = mapping[clean[i]]
            if (code != null && code != prevCode) {
                result.append(code)
                if (result.length >= 4) break
            }
            prevCode = code ?: '0'
        }

        while (result.length < 4) {
            result.append('0')
        }

        return result.toString()
    }
}

private data class StationData(
    val name: String,
    val crs: String,
    val aliases: List<String>
)
