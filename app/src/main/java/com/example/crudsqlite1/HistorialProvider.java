package com.example.crudsqlite1;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Semana 7, Actividad 03: envuelve la base del historial para que OTRA
 * aplicacion pueda leerla.
 *
 * La segunda app nunca toca el archivo .db (no podria: vive en el
 * almacenamiento privado de esta aplicacion). Le pide los datos al sistema
 * mediante una URI, y el sistema los pasa por IPC. Asi el control sigue de este
 * lado: aqui se decide que se deja ver y con que permiso.
 */
public class HistorialProvider extends ContentProvider {

    /** Nombre unico del proveedor. Debe coincidir con el manifest. */
    public static final String AUTORIDAD = "com.example.crudsqlite1.historial";
    public static final String TABLA = "historial_sensores";

    /** content://com.example.crudsqlite1.historial/historial_sensores */
    public static final Uri URI_HISTORIAL =
            Uri.parse("content://" + AUTORIDAD + "/" + TABLA);

    // Codigos que devuelve el UriMatcher segun la forma de la URI
    private static final int CODIGO_TODOS = 1;
    private static final int CODIGO_UNO = 2;

    private static final UriMatcher MATCHER = new UriMatcher(UriMatcher.NO_MATCH);

    static {
        // .../historial_sensores        -> todos los registros
        MATCHER.addURI(AUTORIDAD, TABLA, CODIGO_TODOS);
        // .../historial_sensores/5      -> el registro con id 5  (# = un numero)
        MATCHER.addURI(AUTORIDAD, TABLA + "/#", CODIGO_UNO);
    }

    private RegistroSensorDAO dao;

    @Override
    public boolean onCreate() {
        // Se llama al arrancar el proceso, antes que cualquier Activity.
        dao = HistorialDatabase.getInstance(getContext()).registroSensorDao();
        return true;
    }

    @Nullable
    @Override
    public Cursor query(@NonNull Uri uri, @Nullable String[] projection,
                        @Nullable String selection, @Nullable String[] selectionArgs,
                        @Nullable String sortOrder) {
        Cursor cursor;
        switch (MATCHER.match(uri)) {
            case CODIGO_TODOS:
                cursor = dao.consultarCursor();
                break;
            case CODIGO_UNO:
                cursor = dao.consultarCursorPorId(ContentUris.parseId(uri));
                break;
            default:
                throw new IllegalArgumentException("URI no reconocida: " + uri);
        }
        // Con esto, si la tabla cambia, los clientes que observen esta URI se
        // enteran solos y vuelven a consultar.
        if (getContext() != null) {
            cursor.setNotificationUri(getContext().getContentResolver(), uri);
        }
        return cursor;
    }

    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
        if (MATCHER.match(uri) != CODIGO_TODOS || values == null) {
            throw new IllegalArgumentException("No se puede insertar en: " + uri);
        }

        RegistroSensor registro = new RegistroSensor(
                values.getAsString("tipoSensor"),
                values.getAsDouble("valorLectura"),
                values.getAsLong("timestamp"),
                Boolean.TRUE.equals(values.getAsBoolean("estadoAlerta")));

        long id = dao.insertar(registro);

        if (getContext() != null) {
            getContext().getContentResolver().notifyChange(uri, null);
        }
        return ContentUris.withAppendedId(URI_HISTORIAL, id);
    }

    /**
     * Le dice al sistema que tipo de dato devuelve cada URI. Android lo usa, por
     * ejemplo, para decidir que aplicacion puede abrir un dato compartido.
     */
    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        switch (MATCHER.match(uri)) {
            case CODIGO_TODOS:
                return "vnd.android.cursor.dir/vnd." + AUTORIDAD + "." + TABLA;
            case CODIGO_UNO:
                return "vnd.android.cursor.item/vnd." + AUTORIDAD + "." + TABLA;
            default:
                return null;
        }
    }

    /* La app de auditoria solo debe LEER. Borrar y actualizar quedan cerrados a
       proposito: un cliente externo no tiene por que modificar el historial. */
    @Override
    public int delete(@NonNull Uri uri, @Nullable String s, @Nullable String[] args) {
        throw new UnsupportedOperationException("El historial no se borra desde fuera");
    }

    @Override
    public int update(@NonNull Uri uri, @Nullable ContentValues v,
                      @Nullable String s, @Nullable String[] args) {
        throw new UnsupportedOperationException("El historial no se modifica desde fuera");
    }
}
