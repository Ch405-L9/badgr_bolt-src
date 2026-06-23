package com.badgr.orbreader.data.model

import java.util.UUID

enum class FileType { TXT, PDF, EPUB, DOCX, IMAGE }

data class Book(
    val id        : String   = UUID.randomUUID().toString(),
    val title     : String,
    val fileType  : FileType,
    val wordCount : Int,
    val createdAt : Long     = System.currentTimeMillis(),
    val coverPath : String?  = null,
    val category  : String?  = null
)
