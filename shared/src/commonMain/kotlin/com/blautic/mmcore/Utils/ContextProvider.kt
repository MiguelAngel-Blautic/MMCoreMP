package Utils

expect class ContextProvider {
    companion object {
        fun getContext(): Any?
    }
}