package com.example.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        BrokerAccountEntity::class,
        EaInstallationEntity::class,
        EaLicenseEntity::class,
        PositionEntity::class,
        ClosedTradeEntity::class,
        PresetEntity::class,
        ConfigAuditEntity::class,
        RiskProfileEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class XauPilotDatabase : RoomDatabase() {

    abstract fun xauPilotDao(): XauPilotDao

    companion object {
        @Volatile
        private var INSTANCE: XauPilotDatabase? = null

        fun getInstance(context: Context): XauPilotDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    XauPilotDatabase::class.java,
                    "xau_pilot_realdata.db"
                )
                .fallbackToDestructiveMigration()
                .build()
                .also { INSTANCE = it }
            }
        }
    }
}
