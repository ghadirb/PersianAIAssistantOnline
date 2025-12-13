package com.persianai.assistant.utils

/**
 * Centralized provider for Neshan API key/license.
 * The raw license is stored here and can be obfuscated (whitespace removed) before use.
 */
object NeshanKeyProvider {
    // Raw license provided by user (free tier)
    private const val LICENSE_RAW = """
30608MC0CFQCJn+6tm6kXJ85wwKkUmmlWO4
R7vQIUOF24W8aqQsnGOdc5JdHIkj1KdcI=N
k0fHQIDD1MvNRURCEBbRgIBBE1VS1BfVVoX
FRZDUDkKARkAFAoCcEMPFB4WDglDQktBQFB
YU00UDxsCVlJ8DkNCWVZUQnkDCFJVU1gAVw
8PGAkABwMFVwFdA1UDK1xAElUFQwxoEgUGA
wkEHRJMU3sSDndyDHJ0DXF1dwxdMTVSXlAU
CVgvWFlaUyFYKy1mdQoBcAEDcHoNBBMQQ1E
YCAUETjkEJQ8LDUNeGkwcVVldHkZVREEbDh
YELF0WR1YCBw0vUFpQQFYCAlZDUBkFCh5UB
1dQCVFRBnsLRBdDSkNHLw8JEwwWDhZMAAhN
VRsJFFdaVEpHFF4/CF0GAAwMQSRPHA0MFgc
LEwMLQVNSVlhQGEZcRkJYLgpdOAgFBE45BD
8EHxQIBwRMRQJXXF1TRldBVkYZWD1NSVZQX
1MOe1dUT1xRVUpQX1gCHBtUU1pTR1hAW0Ng
GRYGEg8OTmhbThdcQE1GAhwMQURcHldAFA8
bBgQDeEJCRExUU3R4U1ZRWFhVUE9dWBgGDw
EHAgQXFRZTVCMKARUVCRMNIg4fFU9YQxQND
x1GX0teG1ZXVlJRWlVgWEVBVlBURH5UWUwD
ExtWD0wUDBJJUlVfV1JcelVcKE1JVgIJDA4
6BB4SBAMPBQhACFNDUEBCVVhBGxgWRTQfFl
ZbRABOLhMDCAlAHA==
"""

    /**
     * Returns the usable API key/license string for Neshan endpoints.
     */
    fun getApiKey(): String = LICENSE_RAW.replace("\\s".toRegex(), "").trim()
}
