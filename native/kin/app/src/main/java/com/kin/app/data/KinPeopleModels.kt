package com.kin.app.data

data class KinRemotePerson(
    val id: String,
    val displayName: String,
    val username: String,
    val bio: String,
    val skinId: String,
    val relationship: String,
) {
    val handle: String get() = "@$username"
}

data class KinFriendRequest(
    val id: Int,
    val person: KinRemotePerson,
)

data class KinFriendRequests(
    val incoming: List<KinFriendRequest> = emptyList(),
    val outgoing: List<KinFriendRequest> = emptyList(),
)

sealed interface KinPeopleResult<out T> {
    data class Success<T>(val value: T) : KinPeopleResult<T>
    data class Error(val message: String) : KinPeopleResult<Nothing>
}
