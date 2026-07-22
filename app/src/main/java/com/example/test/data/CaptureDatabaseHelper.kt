package com.example.test.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log

data class BirdCapture(
    val id: Long,
    val speciesName: String,
    val scientificName: String,
    val confidence: Float,
    val timestamp: Long,
    val imagePath: String,
    val latitude: Double?,
    val longitude: Double?
)

class CaptureDatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val TAG = "CaptureDatabaseHelper"
        private const val DATABASE_NAME = "bird_captures.db"
        private const val DATABASE_VERSION = 1

        private const val TABLE_CAPTURES = "captures"
        private const val COLUMN_ID = "id"
        private const val COLUMN_SPECIES_NAME = "species_name"
        private const val COLUMN_SCIENTIFIC_NAME = "scientific_name"
        private const val COLUMN_CONFIDENCE = "confidence"
        private const val COLUMN_TIMESTAMP = "timestamp"
        private const val COLUMN_IMAGE_PATH = "image_path"
        private const val COLUMN_LATITUDE = "latitude"
        private const val COLUMN_LONGITUDE = "longitude"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTableQuery = """
            CREATE TABLE $TABLE_CAPTURES (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_SPECIES_NAME TEXT NOT NULL,
                $COLUMN_SCIENTIFIC_NAME TEXT NOT NULL,
                $COLUMN_CONFIDENCE REAL NOT NULL,
                $COLUMN_TIMESTAMP INTEGER NOT NULL,
                $COLUMN_IMAGE_PATH TEXT NOT NULL,
                $COLUMN_LATITUDE REAL,
                $COLUMN_LONGITUDE REAL
            )
        """.trimIndent()
        db.execSQL(createTableQuery)
        Log.d(TAG, "Database captures table created.")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_CAPTURES")
        onCreate(db)
    }

    fun insertCapture(
        speciesName: String,
        scientificName: String,
        confidence: Float,
        imagePath: String,
        latitude: Double?,
        longitude: Double?
    ): Long {
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_SPECIES_NAME, speciesName)
            put(COLUMN_SCIENTIFIC_NAME, scientificName)
            put(COLUMN_CONFIDENCE, confidence)
            put(COLUMN_TIMESTAMP, System.currentTimeMillis())
            put(COLUMN_IMAGE_PATH, imagePath)
            if (latitude != null) put(COLUMN_LATITUDE, latitude) else putNull(COLUMN_LATITUDE)
            if (longitude != null) put(COLUMN_LONGITUDE, longitude) else putNull(COLUMN_LONGITUDE)
        }
        val id = db.insert(TABLE_CAPTURES, null, values)
        Log.d(TAG, "Inserted capture at row $id: $speciesName")
        return id
    }

    fun getAllCaptures(): List<BirdCapture> {
        val captureList = mutableListOf<BirdCapture>()
        val db = this.readableDatabase
        val cursor = db.rawQuery("SELECT * FROM $TABLE_CAPTURES ORDER BY $COLUMN_TIMESTAMP DESC", null)

        cursor.use { c ->
            val idIndex = c.getColumnIndexOrThrow(COLUMN_ID)
            val speciesIndex = c.getColumnIndexOrThrow(COLUMN_SPECIES_NAME)
            val scientificIndex = c.getColumnIndexOrThrow(COLUMN_SCIENTIFIC_NAME)
            val confidenceIndex = c.getColumnIndexOrThrow(COLUMN_CONFIDENCE)
            val timestampIndex = c.getColumnIndexOrThrow(COLUMN_TIMESTAMP)
            val imageIndex = c.getColumnIndexOrThrow(COLUMN_IMAGE_PATH)
            val latIndex = c.getColumnIndexOrThrow(COLUMN_LATITUDE)
            val lonIndex = c.getColumnIndexOrThrow(COLUMN_LONGITUDE)

            while (c.moveToNext()) {
                val latitude = if (c.isNull(latIndex)) null else c.getDouble(latIndex)
                val longitude = if (c.isNull(lonIndex)) null else c.getDouble(lonIndex)

                captureList.add(
                    BirdCapture(
                        id = c.getLong(idIndex),
                        speciesName = c.getString(speciesIndex),
                        scientificName = c.getString(scientificIndex),
                        confidence = c.getFloat(confidenceIndex),
                        timestamp = c.getLong(timestampIndex),
                        imagePath = c.getString(imageIndex),
                        latitude = latitude,
                        longitude = longitude
                    )
                )
            }
        }
        return captureList
    }

    fun deleteCapture(id: Long): Boolean {
        val db = this.writableDatabase
        val deletedRows = db.delete(TABLE_CAPTURES, "$COLUMN_ID = ?", arrayOf(id.toString()))
        Log.d(TAG, "Deleted capture ID $id, rows affected: $deletedRows")
        return deletedRows > 0
    }
}
