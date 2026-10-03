package com.example.crudsqlite1;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Una fila del historial: UNA lectura de UN sensor en UN instante.
 *
 * Cada vez que Firebase avisa de un cambio en tarjeta/sensores llegan varios
 * valores juntos (luz, temperatura, humedad, distancia). Se guarda una fila por
 * sensor y no una fila con todas las columnas, porque asi agregar un sensor
 * nuevo no obliga a cambiar la tabla: basta con otro valor de tipoSensor.
 */
@Entity(tableName = "historial_sensores",
        // La consulta del historial ordena por timestamp: con indice, SQLite no
        // tiene que ordenar la tabla completa cada vez.
        indices = {@Index("timestamp")})
public class RegistroSensor {

    @PrimaryKey(autoGenerate = true)
    private int id;                 // 0 = que SQLite asigne el siguiente

    private String tipoSensor;      // "luz", "temperatura", "humedad", "distancia"
    private double valorLectura;
    private long timestamp;         // milisegundos desde 1970, hora del telefono

    /*
     * Columna agregada en la VERSION 2 del esquema.
     *
     * SQLite no tiene tipo BOOLEAN: Room guarda true/false como INTEGER 1/0.
     *
     * defaultValue = "0" no es opcional. La migracion crea la columna con
     * DEFAULT 0 para que las filas viejas tengan valor; despues Room compara la
     * tabla real con esta clase y, si el valor por omision no coincidiera, la
     * app se cerraria con "Migration didn't properly handle historial_sensores".
     */
    @ColumnInfo(defaultValue = "0")
    private boolean estadoAlerta;

    public RegistroSensor() {
    }

    public RegistroSensor(String tipoSensor, double valorLectura, long timestamp) {
        this(tipoSensor, valorLectura, timestamp, false);
    }

    public RegistroSensor(String tipoSensor, double valorLectura, long timestamp, boolean estadoAlerta) {
        this.tipoSensor = tipoSensor;
        this.valorLectura = valorLectura;
        this.timestamp = timestamp;
        this.estadoAlerta = estadoAlerta;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getTipoSensor() {
        return tipoSensor;
    }

    public void setTipoSensor(String tipoSensor) {
        this.tipoSensor = tipoSensor;
    }

    public double getValorLectura() {
        return valorLectura;
    }

    public void setValorLectura(double valorLectura) {
        this.valorLectura = valorLectura;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public boolean isEstadoAlerta() {
        return estadoAlerta;
    }

    public void setEstadoAlerta(boolean estadoAlerta) {
        this.estadoAlerta = estadoAlerta;
    }

    @Override
    public String toString() {
        return tipoSensor + " = " + valorLectura;
    }
}
