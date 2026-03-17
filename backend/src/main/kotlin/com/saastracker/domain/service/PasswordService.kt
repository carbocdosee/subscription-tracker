package com.saastracker.domain.service

import at.favre.lib.crypto.bcrypt.BCrypt

class PasswordService {
    fun hash(rawPassword: String): String = BCrypt.withDefaults()
        .hashToString(12, rawPassword.toCharArray())

    fun verify(rawPassword: String, hash: String): Boolean = BCrypt.verifyer()
        .verify(rawPassword.toCharArray(), hash)
        .verified
}

