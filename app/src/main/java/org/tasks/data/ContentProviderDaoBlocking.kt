package org.tasks.data

import android.database.Cursor
import android.database.MatrixCursor
import androidx.room3.useReaderConnection
import androidx.sqlite.SQLiteStatement
import kotlinx.coroutines.runBlocking
import org.tasks.data.db.Database
import javax.inject.Inject

class ContentProviderDaoBlocking @Inject constructor(
    private val database: Database,
) {
    fun getTasks(): Cursor = runBlocking { rawQuery("SELECT * FROM tasks") }

    fun getLists(): Cursor = runBlocking {
        rawQuery("""
            SELECT caldav_lists.*, caldav_accounts.cda_name
            FROM caldav_lists
            INNER JOIN caldav_accounts ON cdl_account = cda_uuid
            """.trimIndent()
        )
    }

    fun rawQuery(query: String): Cursor = runBlocking {
        database.useReaderConnection { transactor ->
            transactor.usePrepared(query) { it.toCursor() }
        }
    }
}

private fun SQLiteStatement.toCursor(): Cursor {
    val cursor = MatrixCursor(getColumnNames().toTypedArray())
    while (step()) {
        cursor.addRow((0 until getColumnCount()).map { getTextOrNull(it) })
    }
    return cursor
}
