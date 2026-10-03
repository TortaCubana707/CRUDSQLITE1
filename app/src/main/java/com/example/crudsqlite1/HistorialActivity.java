package com.example.crudsqlite1;

import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.widget.TextView;

/**
 * Muestra el historial guardado en Room.
 *
 * No consulta la base a mano: observa el LiveData del DAO. Cada vez que la
 * pantalla principal inserta filas, Room vuelve a ejecutar la consulta y avisa
 * aqui, asi que las lecturas nuevas aparecen solas mientras la pantalla esta
 * abierta.
 */
public class HistorialActivity extends AppCompatActivity {

    private HistorialAdapter adaptador;
    private TextView txtTotal, txtVacio;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_historial);
        setTitle("Historial local (Room)");

        txtTotal = findViewById(R.id.txt_total);
        txtVacio = findViewById(R.id.txt_vacio);

        RecyclerView lista = findViewById(R.id.lista_historial);
        lista.setLayoutManager(new LinearLayoutManager(this));
        adaptador = new HistorialAdapter();
        lista.setAdapter(adaptador);

        RegistroSensorDAO dao = HistorialDatabase.getInstance(this).registroSensorDao();

        // observe() entrega la lista en el hilo principal y se desconecta solo
        // cuando la Activity se destruye: no hay que acordarse de nada.
        dao.obtenerHistorial().observe(this, registros -> {
            adaptador.actualizar(registros);
            txtVacio.setVisibility(registros.isEmpty() ? View.VISIBLE : View.GONE);
        });

        // Dos consultas distintas sobre la misma tabla; la segunda usa la
        // columna estadoAlerta que agrego la version 2 del esquema.
        dao.contarRegistros().observe(this, total -> {
            this.totalRegistros = total;
            pintarEncabezado();
        });
        dao.contarAlertas().observe(this, alertas -> {
            this.totalAlertas = alertas;
            pintarEncabezado();
        });
    }

    private int totalRegistros = 0;
    private int totalAlertas = 0;

    private void pintarEncabezado() {
        txtTotal.setText(totalRegistros + " lecturas en Room · " + totalAlertas + " en alerta");
    }
}
