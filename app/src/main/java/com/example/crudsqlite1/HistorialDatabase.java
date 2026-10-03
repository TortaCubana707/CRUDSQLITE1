package com.example.crudsqlite1;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.annotation.NonNull;
import androidx.sqlite.db.SupportSQLiteDatabase;

/**
 * Base de datos local del historial de sensores.
 *
 * Es abstracta porque Room genera la clase concreta al compilar
 * (HistorialDatabase_Impl).
 */
/*
 * version = 2: se agrego la columna estadoAlerta. Subir el numero es lo que le
 * avisa a Room que el esquema cambio. Si se cambia la entidad pero NO la
 * version, Room detecta que no coincide y lanza IllegalStateException al abrir.
 */
@Database(entities = {RegistroSensor.class}, version = 2, exportSchema = true)
public abstract class HistorialDatabase extends RoomDatabase {

    public abstract RegistroSensorDAO registroSensorDao();

    private static HistorialDatabase instancia;

    /**
     * Migracion de la version 1 a la 2.
     *
     * Sin este objeto Room no sabria como pasar de un esquema al otro: o falla
     * al abrir, o habria que usar fallbackToDestructiveMigration() (como en la
     * practica DaoCrud), que BORRA la tabla y pierde todo el historial del
     * telefono. ALTER TABLE ... ADD COLUMN agrega la columna y deja intactas
     * las filas que ya existian.
     *
     * Room ejecuta migrate() una sola vez: sabe en que version esta el archivo
     * por PRAGMA user_version, un numero que SQLite guarda dentro del .db.
     */
    public static final Migration MIGRACION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE historial_sensores "
                    + "ADD COLUMN estadoAlerta INTEGER NOT NULL DEFAULT 0");
        }
    };

    /**
     * Patron Singleton.
     *
     * Abrir la base cuesta (crea conexiones y caches) y, sobre todo, dos
     * instancias abiertas sobre el mismo archivo pueden pisarse las escrituras
     * y provocar "database is locked". synchronized evita que dos hilos la
     * construyan a la vez.
     *
     * A diferencia de la practica anterior (DaoCrud), aqui NO se usa
     * allowMainThreadQueries(): las inserciones van en un hilo de fondo para no
     * congelar la pantalla mientras se escribe en disco.
     */
    public static synchronized HistorialDatabase getInstance(Context context) {
        if (instancia == null) {
            instancia = Room.databaseBuilder(
                            context.getApplicationContext(),  // nunca una Activity: la retendria en memoria
                            HistorialDatabase.class,
                            "historial_froster.db")
                    .addMigrations(MIGRACION_1_2)
                    .build();
        }
        return instancia;
    }
}
