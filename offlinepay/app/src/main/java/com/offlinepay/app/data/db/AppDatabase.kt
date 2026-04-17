package com.offlinepay.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.offlinepay.app.data.model.TransactionEntity
import com.offlinepay.app.data.model.TxDirection
import com.offlinepay.app.data.model.TxStatus

class Converters {
    @TypeConverter fun statusToString(s: TxStatus) = s.name
    @TypeConverter fun stringToStatus(s: String) = TxStatus.valueOf(s)
    @TypeConverter fun dirToString(d: TxDirection) = d.name
    @TypeConverter fun stringToDir(s: String) = TxDirection.valueOf(s)
}

@Database(entities = [TransactionEntity::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
}
