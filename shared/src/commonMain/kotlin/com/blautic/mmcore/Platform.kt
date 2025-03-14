package com.blautic.mmcore

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform