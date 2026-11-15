package com.example.prismstudio.p40b43a88.data.repository

/** A file the user picked with the system document picker. */
class PickedDocument(val name: String, val bytes: ByteArray)

/** Thrown when a picked document cannot be used; [message] is shown to the user. */
class DocumentFailure(message: String) : Exception(message)

/** Reads documents the user picked (Storage Access Framework uris). */
interface DocumentRepository {
    /** The name and bytes of the document at [uri]; throws [DocumentFailure] when it is unreadable or larger than [maxBytes]. */
    suspend fun read(uri: String, maxBytes: Int): PickedDocument

    /** Reads nothing: for previews and tests without a content resolver. */
    object None : DocumentRepository {
        override suspend fun read(uri: String, maxBytes: Int): PickedDocument = throw DocumentFailure("无法读取文件")
    }
}
