package com.example.crudsqlite1;

import android.os.Bundle;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

/**
 * Panel del sistema de enfriamiento FROSTER.
 *
 * Lee la rama tarjeta/refrigeracion que escribe la ESP32 y deja cambiar el
 * objetivo y el modo desde el telefono. La rama tarjeta/sensores de la practica
 * anterior no se toca: son ramas hermanas y cada pantalla usa la suya.
 *
 * Tres nodos, tres papeles distintos:
 *   lecturas/  la placa escribe, aqui solo se muestran
 *   estado/    la placa dice que esta encendido y por que
 *   config/    esta pantalla escribe, la placa obedece por stream
 */
public class RefrigeracionActivity extends AppCompatActivity {

    // 1. Vistas
    private TextView txtAlerta, txtDelta, txtAireSalida, txtAmbiente;
    private TextView txtPlacaFria, txtRocio, txtCeldaA, txtCeldaB;
    private TextView txtVentiladores, txtPotencia, txtObjetivo;
    private SeekBar barraObjetivo;
    private SwitchCompat swModoManual, swCeldaA, swCeldaB;

    // 2. Firebase
    private DatabaseReference refLecturas, refEstado, refConfig;

    /* Mientras el usuario arrastra la barra no hay que sobrescribirla con lo
       que llega de la base, o el control se pelea con el dedo. */
    private boolean usuarioArrastrando = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_refrigeracion);
        setTitle("FROSTER - Enfriamiento");

        txtAlerta       = findViewById(R.id.txt_alerta);
        txtDelta        = findViewById(R.id.txt_delta);
        txtAireSalida   = findViewById(R.id.txt_aire_salida);
        txtAmbiente     = findViewById(R.id.txt_ambiente);
        txtPlacaFria    = findViewById(R.id.txt_placa_fria);
        txtRocio        = findViewById(R.id.txt_rocio);
        txtCeldaA       = findViewById(R.id.txt_celda_a);
        txtCeldaB       = findViewById(R.id.txt_celda_b);
        txtVentiladores = findViewById(R.id.txt_ventiladores);
        txtPotencia     = findViewById(R.id.txt_potencia);
        txtObjetivo     = findViewById(R.id.txt_objetivo);
        barraObjetivo   = findViewById(R.id.barra_objetivo);
        swModoManual    = findViewById(R.id.sw_modo_manual);
        swCeldaA        = findViewById(R.id.sw_celda_a);
        swCeldaB        = findViewById(R.id.sw_celda_b);

        FirebaseDatabase base = FirebaseDatabase.getInstance();
        refLecturas = base.getReference("tarjeta/refrigeracion/lecturas");
        refEstado   = base.getReference("tarjeta/refrigeracion/estado");
        refConfig   = base.getReference("tarjeta/refrigeracion/config");

        configurarControles();

        FirebaseAuth.getInstance().signInWithEmailAndPassword("esp32@correo.com", "tortacubana")
                .addOnCompleteListener(tarea -> {
                    if (tarea.isSuccessful()) {
                        /* Igual que en MainActivity: los listeners se enganchan
                           DESPUES del login. Registrarlos antes hace que la base
                           los rechace con Permission denied y la pantalla se
                           queda en guiones sin decir por que. */
                        escucharLecturas();
                        escucharEstado();
                    } else {
                        String error = tarea.getException() != null
                                ? tarea.getException().getMessage() : "Error desconocido";
                        Toast.makeText(this, "Error de Login: " + error, Toast.LENGTH_LONG).show();
                    }
                });
    }

    // ---------------------------------------------------------------
    // ESCRIBIR EN FIREBASE (esta pantalla manda)
    // ---------------------------------------------------------------
    private void configurarControles() {
        /* La barra va de 0 a 28 y representa 18.0 a 32.0 en pasos de 0.5. Se
           usan enteros porque SeekBar no maneja decimales. El rango es el que
           ASHRAE permite para aire de entrada de equipo de computo. */
        barraObjetivo.setMax(28);
        barraObjetivo.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar barra, int valor, boolean deUsuario) {
                txtObjetivo.setText("Objetivo: " + String.format("%.1f", posicionAGrados(valor)) + " C");
            }

            @Override
            public void onStartTrackingTouch(SeekBar barra) { usuarioArrastrando = true; }

            @Override
            public void onStopTrackingTouch(SeekBar barra) {
                usuarioArrastrando = false;
                refConfig.child("setpoint").setValue(posicionAGrados(barra.getProgress()));
            }
        });

        swModoManual.setOnCheckedChangeListener((vista, marcado) -> {
            refConfig.child("modo").setValue(marcado ? "manual" : "auto");
            swCeldaA.setEnabled(marcado);
            swCeldaB.setEnabled(marcado);
        });

        /* Estos dos solo sirven en modo manual. Aunque se manden, el firmware
           ignora la orden si el disipador de esa celda esta sobrecalentado: la
           proteccion vive en la placa, no aqui. */
        swCeldaA.setOnCheckedChangeListener((vista, marcado) -> {
            if (vista.isPressed()) refConfig.child("manual_a").setValue(marcado);
        });
        swCeldaB.setOnCheckedChangeListener((vista, marcado) -> {
            if (vista.isPressed()) refConfig.child("manual_b").setValue(marcado);
        });

        Button btnAuto = findViewById(R.id.btn_auto);
        btnAuto.setOnClickListener(v -> {
            refConfig.child("modo").setValue("auto");
            swModoManual.setChecked(false);
            Toast.makeText(this, "Modo automatico", Toast.LENGTH_SHORT).show();
        });
    }

    private float posicionAGrados(int posicion) { return 18.0f + (posicion * 0.5f); }
    private int   gradosAPosicion(double grados) { return (int) Math.round((grados - 18.0) * 2); }

    // ---------------------------------------------------------------
    // LEER DE FIREBASE
    // ---------------------------------------------------------------
    private void escucharLecturas() {
        refLecturas.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot datos) {
                if (!datos.exists()) return;

                double delta = leerNumero(datos.child("delta_t"));
                txtDelta.setText(String.format("%.1f C bajo el ambiente", delta));

                txtAireSalida.setText(String.format("Aire de entrada al rack: %.1f C",
                        leerNumero(datos.child("aire_salida"))));
                txtAmbiente.setText(String.format("Ambiente: %.1f C  ·  %.0f %% HR",
                        leerNumero(datos.child("ambiente")), leerNumero(datos.child("humedad"))));
                txtPlacaFria.setText(String.format("Placa fria: %.1f C",
                        leerNumero(datos.child("placa_fria"))));

                /* El margen sobre el punto de rocio es el numero que dice si el
                   sistema esta a punto de condensar agua dentro del rack. */
                double margen = leerNumero(datos.child("placa_fria"))
                              - leerNumero(datos.child("punto_rocio"));
                txtRocio.setText(String.format("Rocio: %.1f C  (margen %.1f C)",
                        leerNumero(datos.child("punto_rocio")), margen));

                txtPotencia.setText(String.format("Consumo: %.0f W  ·  %.1f A",
                        leerNumero(datos.child("potencia")), leerNumero(datos.child("corriente"))));
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(RefrigeracionActivity.this,
                        "Firebase cancelo: " + error.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void escucharEstado() {
        refEstado.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot datos) {
                if (!datos.exists()) return;

                txtCeldaA.setText("Celda A: " + textoDeCelda(
                        Boolean.TRUE.equals(datos.child("celda_a").getValue(Boolean.class)),
                        Boolean.TRUE.equals(datos.child("bloqueo_a").getValue(Boolean.class)),
                        leerNumero(datos.child("disipador_a"))));
                txtCeldaB.setText("Celda B: " + textoDeCelda(
                        Boolean.TRUE.equals(datos.child("celda_b").getValue(Boolean.class)),
                        Boolean.TRUE.equals(datos.child("bloqueo_b").getValue(Boolean.class)),
                        leerNumero(datos.child("disipador_b"))));

                txtVentiladores.setText(String.format("Ventiladores: %.0f %%",
                        leerNumero(datos.child("pwm_fans"))));

                String alerta = datos.child("alerta").getValue(String.class);
                if (alerta != null && !alerta.isEmpty()) {
                    txtAlerta.setText("Aviso: " + alerta);
                    txtAlerta.setVisibility(android.view.View.VISIBLE);
                } else {
                    txtAlerta.setVisibility(android.view.View.GONE);
                }

                String modo = datos.child("modo").getValue(String.class);
                boolean manual = "manual".equals(modo);
                swModoManual.setChecked(manual);
                swCeldaA.setEnabled(manual);
                swCeldaB.setEnabled(manual);

                if (!usuarioArrastrando) {
                    double objetivo = leerNumero(datos.child("setpoint"));
                    barraObjetivo.setProgress(gradosAPosicion(objetivo));
                    txtObjetivo.setText(String.format("Objetivo: %.1f C", objetivo));
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) { }
        });
    }

    private String textoDeCelda(boolean encendida, boolean bloqueada, double disipador) {
        if (bloqueada) return String.format("BLOQUEADA por calor (%.1f C)", disipador);
        return String.format("%s  ·  disipador %.1f C", encendida ? "encendida" : "apagada", disipador);
    }

    /**
     * Realtime Database devuelve los numeros como Long cuando no traen decimales
     * y como Double cuando si. Pedir getValue(Double.class) sobre un Long lanza
     * excepcion, y pedir Integer sobre un Long devuelve null en silencio: todo
     * se ve en cero y no hay error que seguir. Por eso se lee como Number.
     */
    private double leerNumero(DataSnapshot nodo) {
        Object valor = nodo.getValue();
        if (valor instanceof Number) return ((Number) valor).doubleValue();
        if (valor instanceof String) {
            try { return Double.parseDouble((String) valor); } catch (NumberFormatException e) { return 0; }
        }
        return 0;
    }
}
