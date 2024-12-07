package lt.lastweeknextday.cammask.data

data class FilterSettings(
    var tags: List<String> = emptyList(),
    var orderBy: String = "ratingsCount",
    var orderDirection: String = "desc"
)