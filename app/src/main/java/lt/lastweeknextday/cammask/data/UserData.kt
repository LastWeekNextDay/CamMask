package lt.lastweeknextday.cammask.data

data class UserData(
    val id: String,
    val name: String,
    val photoUrl: String,
    val canComment: Boolean,
    val canUpload: Boolean,
    val creationDate: String,
    val lastAccess: String
)