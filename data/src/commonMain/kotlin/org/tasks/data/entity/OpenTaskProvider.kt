package org.tasks.data.entity

enum class OpenTaskProvider(
    val accountType: String,
    val packageName: String,
    val displayName: String,
) {
    DAVX5("bitfire.at.davdroid", "at.bitfire.davdroid", "DAVx\u2075"),
    DAVX5_MANAGED("com.davdroid", "com.davdroid", "DAVx\u2075"),
    ETESYNC("com.etesync.syncadapter", "com.etesync.syncadapter", "EteSync"),
    DECSYNC("org.decsync.tasks", "org.decsync.cc", "DecSync CC"),
    KSYNC("infomaniak.com.sync", "com.infomaniak.sync", "kSync"),
    DAVSKY("com.rypox.davsky", "com.rypox.davsky", "DAVsky"),
    SILENTSUITE("io.silentsuite.android", "io.silentsuite.android", "SilentSuite"),
    ;

    companion object {
        fun fromUuid(uuid: String?) = entries.find { uuid?.startsWith(it.accountType) == true }

        val SUPPORTED_TYPES = entries.map { it.accountType }.toSet()

        val SUPPORTED_TYPE_FILTER =
            SUPPORTED_TYPES.joinToString(" OR ") { "ACCOUNT_TYPE = '$it'" }
    }
}
