package com.example.crudsqlite1;

import android.database.Cursor;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

/**
 * Operaciones sobre la tabla historial_sensores.
 *
 * Room escribe la implementacion al compilar. Si una consulta menciona una
 * columna que no existe, el proyecto NO compila; con SQL en cadenas de texto el
 * error solo aparecia al ejecutar.
 */
@Dao
public interface RegistroSensorDAO {

    /** Inserta una lectura y devuelve el id que le asigno SQLite. */
    @Insert
    long insertar(RegistroSensor registro);

    /**
     * Inserta varias lecturas en UNA sola transaccion. Para las cuatro lecturas
     * que llegan juntas de Firebase es mas rapido que cuatro inserciones
     * sueltas, y ademas es atomico: entran todas o no entra ninguna.
     */
    @Insert
    List<Long> insertarTodos(List<RegistroSensor> registros);

    /**
     * Historial completo, lo mas reciente primero.
     *
     * Devuelve LiveData: Room vuelve a ejecutar la consulta sola cada vez que
     * la tabla cambia y avisa a la pantalla. Por eso el RecyclerView se
     * actualiza sin que nadie lo recargue a mano. Ademas LiveData respeta el
     * ciclo de vida: no notifica si la Activity no esta visible.
     */
    @Query("SELECT * FROM historial_sensores ORDER BY timestamp DESC, id DESC")
    LiveData<List<RegistroSensor>> obtenerHistorial();

    /**
     * El mismo historial pero como Cursor, que es lo que exige la API de
     * ContentProvider (Semana 7). Room genera la consulta igual; solo cambia el
     * tipo de retorno.
     */
    @Query("SELECT * FROM historial_sensores ORDER BY timestamp DESC, id DESC")
    Cursor consultarCursor();

    @Query("SELECT * FROM historial_sensores WHERE id = :id")
    Cursor consultarCursorPorId(long id);

    @Query("SELECT COUNT(*) FROM historial_sensores")
    LiveData<Integer> contarRegistros();

    /** Cuantas lecturas quedaron en alerta. Usa la columna de la version 2. */
    @Query("SELECT COUNT(*) FROM historial_sensores WHERE estadoAlerta = 1")
    LiveData<Integer> contarAlertas();
}
