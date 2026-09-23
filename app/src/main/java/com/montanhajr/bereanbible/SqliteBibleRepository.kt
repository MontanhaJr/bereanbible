package com.montanhajr.bereanbible

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import java.io.FileOutputStream

class SqliteBibleRepository(private val context: Context) : BibleRepository {

    private val dbName = "rv1865.db"

    private val bookIdToNumber: Map<String, Int> by lazy {
        BibleData.BOOKS.mapIndexed { index, book -> book.id to (index + 1) }.toMap()
    }

    private val numberToBookId: Map<Int, String> by lazy {
        BibleData.BOOKS.mapIndexed { index, book -> (index + 1) to book.id }.toMap()
    }

    private var database: SQLiteDatabase? = null

    @Synchronized
    private fun getDatabase(): SQLiteDatabase? {
        if (database?.isOpen == true) return database

        return try {
            val dbFile = context.getDatabasePath(dbName)
            if (!dbFile.exists()) {
                dbFile.parentFile?.mkdirs()
                context.assets.open(dbName).use { input ->
                    FileOutputStream(dbFile).use { output ->
                        input.copyTo(output)
                    }
                }
                Log.d("SqliteBibleRepository", "Copiado $dbName para ${dbFile.path}")
            }
            database = SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READONLY)
            database
        } catch (e: Exception) {
            Log.e("SqliteBibleRepository", "Erro ao abrir banco de dados $dbName", e)
            null
        }
    }

    override fun getBooks(): List<BibleBook> = BibleData.BOOKS

    override fun getBook(id: String): BibleBook? = BibleData.BOOKS.find { it.id == id }

    override fun getChapter(bookId: String, chapter: Int): List<BibleVerse> {
        val bookNum = bookIdToNumber[bookId] ?: return emptyList()
        val db = getDatabase() ?: return emptyList()
        val verses = mutableListOf<BibleVerse>()

        try {
            val cursor = db.rawQuery(
                "SELECT verse, text FROM SpaRV1865_verses WHERE book_id = ? AND chapter = ? ORDER BY verse ASC",
                arrayOf(bookNum.toString(), chapter.toString())
            )
            cursor.use { c ->
                val verseCol = c.getColumnIndexOrThrow("verse")
                val textCol = c.getColumnIndexOrThrow("text")
                while (c.moveToNext()) {
                    val v = c.getInt(verseCol)
                    val text = c.getString(textCol).trim()
                    verses.add(BibleVerse(bookId, chapter, v, text))
                }
            }
        } catch (e: Exception) {
            Log.e("SqliteBibleRepository", "Erro ao buscar capítulo $bookId $chapter", e)
        }

        return verses
    }

    override fun searchText(query: String): List<BibleVerse> {
        if (query.isBlank()) return emptyList()
        val db = getDatabase() ?: return emptyList()
        val results = mutableListOf<BibleVerse>()

        try {
            val cursor = db.rawQuery(
                "SELECT book_id, chapter, verse, text FROM SpaRV1865_verses WHERE text LIKE ? ORDER BY book_id, chapter, verse LIMIT 100",
                arrayOf("%$query%")
            )
            cursor.use { c ->
                val bookNumCol = c.getColumnIndexOrThrow("book_id")
                val chapCol = c.getColumnIndexOrThrow("chapter")
                val verseCol = c.getColumnIndexOrThrow("verse")
                val textCol = c.getColumnIndexOrThrow("text")
                while (c.moveToNext()) {
                    val bNum = c.getInt(bookNumCol)
                    val bId = numberToBookId[bNum] ?: continue
                    val ch = c.getInt(chapCol)
                    val v = c.getInt(verseCol)
                    val text = c.getString(textCol).trim()
                    results.add(BibleVerse(bId, ch, v, text))
                }
            }
        } catch (e: Exception) {
            Log.e("SqliteBibleRepository", "Erro ao buscar texto por '$query'", e)
        }

        return results
    }
}
