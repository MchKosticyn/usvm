package org.usvm.machine.state.pinnedValues

enum class JcSpringPinnedValueSource {
    REQUEST_HEADER,
    REQUEST_PARAM,
    REQUEST_PATH_VARIABLE,
    REQUEST_PATH,
    REQUEST_METHOD,
    REQUEST_QUERY,
    REQUEST_MATRIX,
    REQUEST_BODY,
    REQUEST_HAS_BODY,
    REQUEST_USER_NAME,
    REQUEST_USER_PASSWORD,
    REQUEST_USER_AUTHORITIES,
    REQUEST_COOKIE,
    REQUEST_ATTRIBUTE,
    RESPONSE;

    fun caseSensitive(): Boolean {
        // TODO: Research specifications and sources #AA
        return when(this) {
            REQUEST_ATTRIBUTE -> false
            else -> true
        }
    }
}